package org.reactome.webservice;

import org.json.JSONObject;
import org.reactome.model.ChEBIEntity;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

public class ChEBIRESTLookup {

    public Optional<ChEBIEntity> getChEBIEntity(String chEBIIdentifier) throws IOException, InterruptedException {
        if (chEBIIdentifier == null || chEBIIdentifier.isEmpty()) {
            throw new IllegalStateException("identifier is empty");
        }

        String restURL = "https://www.ebi.ac.uk/chebi/backend/api/public/compound/" + chEBIIdentifier;

        JSONObject chEBIJSON = sendRequestToRESTfulAPI(restURL);
        if (noCompoundMatches(chEBIJSON)) {
            return Optional.empty();
        }

        return Optional.of(parseChEBIEntity(chEBIJSON));
    }

    private JSONObject sendRequestToRESTfulAPI(String restURL) throws IOException, InterruptedException {

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(restURL))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Unable to retrieve ChEBI entity from RESTful API: " + response);
        }

        return new JSONObject(response.body());
    }

    private ChEBIEntity parseChEBIEntity(JSONObject chEBIJSON) {
        String chebiID = chEBIJSON.getString("chebi_accession").replace("CHEBI:", "");
        String name = chEBIJSON.getString("ascii_name");
        String formula = getFormula(chEBIJSON);

        return new ChEBIEntity(chebiID, name, formula);
    }

    private boolean noCompoundMatches(JSONObject chEBIJSON) {
        if (chEBIJSON.has("detail")) {
            return chEBIJSON.getString("detail").contains("No Compound matches");
        }

        return false;
    }

    private String getFormula(JSONObject chEBIJSON) {
        if (chEBIJSON.isNull("chemical_data") ||
            chEBIJSON.getJSONObject("chemical_data").isNull("formula")) {
            return "";
        }

        return chEBIJSON.getJSONObject("chemical_data").getString("formula");
    }
}
