package org.reactome.webservice.helpers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class ChEBIAPIClient {
    private static final String BASE_URL = "https://www.ebi.ac.uk/chebi/backend/api/public/compounds/";

    static final int MAX_RETRIES = 6;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long DEFAULT_INITIAL_BACKOFF_MS = 2000;
    private static final long MAX_BACKOFF_MS = 60_000;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private static final int MAX_LOGGED_BODY_LENGTH = 2000;

    private static final Logger logger = LogManager.getLogger(ChEBIAPIClient.class);

    private final HttpClient httpClient;
    private final long initialBackoffMs;

    public ChEBIAPIClient() {
        this(HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(CONNECT_TIMEOUT)
            .build(), DEFAULT_INITIAL_BACKOFF_MS);
    }

    // For testing
    ChEBIAPIClient(HttpClient httpClient, long initialBackoffMs) {
        this.httpClient = httpClient;
        this.initialBackoffMs = initialBackoffMs;
    }

    public JSONObject fetchCompounds(Set<String> chEBIIdentifiers) throws IOException, InterruptedException {
        if (chEBIIdentifiers == null || chEBIIdentifiers.isEmpty()) {
            throw new IllegalArgumentException("No identifiers to fetch from ChEBI");
        }

        int attemptCount = 0;
        long backoffTime = initialBackoffMs;

        while (true) {
            try {
                return executeRequest(chEBIIdentifiers);
            } catch (IOException e) {
                attemptCount++;

                if (attemptCount >= MAX_RETRIES) {
                    logger.error("Failed to fetch compounds after {} attempts", MAX_RETRIES);
                    throw e;
                }

                long sleepTime = withJitter(backoffTime);
                logger.warn("Attempt {} failed, retrying in {} ms", attemptCount, sleepTime, e);
                Thread.sleep(sleepTime);
                backoffTime = Math.min((long) (backoffTime * BACKOFF_MULTIPLIER), MAX_BACKOFF_MS);
            }
        }
    }

    // "Equal jitter": sleep between half and all of the nominal backoff so concurrent/retrying
    // clients don't hit the server in lockstep.
    private static long withJitter(long backoffTime) {
        long half = backoffTime / 2;
        return half + ThreadLocalRandom.current().nextLong(backoffTime - half + 1);
    }

    private JSONObject executeRequest(Set<String> chEBIIdentifiers) throws IOException, InterruptedException {
        JSONObject payload = new JSONObject();
        payload.put("chebi_ids", new JSONArray(chEBIIdentifiers));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL))
            .header("Content-Type", "application/json")
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();

        // HttpTimeoutException is an IOException, so timed-out requests are retried too
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (isRetryableStatusCode(response.statusCode())) {
            throw new IOException("Received retryable status code " + response.statusCode() +
                " for " + chEBIIdentifiers.size() + " identifier(s); response body: " +
                truncate(response.body()));
        }

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Unable to retrieve ChEBI entity/entities from RESTful API: " +
                response + "; response body: " + truncate(response.body()));
        }

        return new JSONObject(response.body());
    }

    private static String truncate(String body) {
        if (body == null) {
            return "<none>";
        }
        return body.length() <= MAX_LOGGED_BODY_LENGTH ?
            body :
            body.substring(0, MAX_LOGGED_BODY_LENGTH) + "... (" + body.length() + " chars total)";
    }

    private boolean isRetryableStatusCode(int statusCode) {
        final int tooManyRequests = 429;
        final int requestTimeOut = 408;
        final int serverErrorLowerLimit = 500;
        final int serverErrorUpperLimit = 600;

        // Retry on server errors and specific client errors
        return statusCode == tooManyRequests ||
               statusCode == requestTimeOut ||
               (statusCode >= serverErrorLowerLimit && statusCode < serverErrorUpperLimit);
    }
}