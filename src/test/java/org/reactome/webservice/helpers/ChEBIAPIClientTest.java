package org.reactome.webservice.helpers;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChEBIAPIClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<Object> httpResponse;

    private ChEBIAPIClient client;

    @BeforeEach
    void setUp() {
        client = new ChEBIAPIClient(httpClient);
    }

    @Test
    void fetchCompounds_SuccessfulRequest() throws IOException, InterruptedException {
        // Arrange
        String successResponse = "{\"compounds\": [{\"chebiId\": \"CHEBI:15377\", \"name\": \"water\"}]}";
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(successResponse);
        when(httpClient.send(any(), any())).thenReturn(httpResponse);

        // Act
        JSONObject result = client.fetchCompounds(Set.of("CHEBI:15377"));

        // Assert
        assertNotNull(result);
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void fetchCompounds_RetryOnServerError() throws IOException, InterruptedException {
        // Arrange
        String successResponse = "{\"compounds\": []}";
        when(httpResponse.statusCode())
            .thenReturn(500)  // First attempt fails
            .thenReturn(200); // Second attempt succeeds
        when(httpResponse.body()).thenReturn(successResponse);
        when(httpClient.send(any(), any())).thenReturn(httpResponse);

        // Act
        JSONObject result = client.fetchCompounds(Set.of("CHEBI:15377"));

        // Assert
        assertNotNull(result);
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void fetchCompounds_ExceedsMaxRetries() throws IOException, InterruptedException {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(500); // Always return server error
        when(httpClient.send(any(), any())).thenReturn(httpResponse);

        // Act & Assert
        assertThrows(IOException.class, () ->
            client.fetchCompounds(Set.of("CHEBI:15377"))
        );
        verify(httpClient, times(3)).send(any(), any()); // Should try exactly MAX_RETRIES times
    }

    @Test
    void fetchCompounds_NonRetryableClientError() {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(400); // Bad Request
        try {
            when(httpClient.send(any(), any())).thenReturn(httpResponse);
        } catch (IOException | InterruptedException e) {
            fail("Mock setup failed", e);
        }

        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
                client.fetchCompounds(Set.of("CHEBI:15377"))
        );
    }

    @Test
    void fetchCompounds_RetryOn429TooManyRequests() throws IOException, InterruptedException {
        // Arrange
        String successResponse = "{\"compounds\": []}";
        when(httpResponse.statusCode())
            .thenReturn(429)  // First attempt gets rate limited
            .thenReturn(200); // Second attempt succeeds
        when(httpResponse.body()).thenReturn(successResponse);
        when(httpClient.send(any(), any())).thenReturn(httpResponse);

        // Act
        JSONObject result = client.fetchCompounds(Set.of("CHEBI:15377"));

        // Assert
        assertNotNull(result);
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void fetchCompounds_EmptyIdentifierSet() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                client.fetchCompounds(Set.of())
        );
    }

    @Test
    void fetchCompounds_NullIdentifierSet() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                client.fetchCompounds(null)
        );
    }
}