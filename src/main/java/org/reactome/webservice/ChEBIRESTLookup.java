package org.reactome.webservice;

import org.gk.model.GKInstance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.reactome.Utils;
import org.reactome.model.ChEBIEntity;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class ChEBIRESTLookup {

    public Map<GKInstance, Optional<ChEBIEntity>> getChEBIEntities(List<GKInstance> referenceMolecules)
        throws IOException, InterruptedException {

        if (referenceMolecules == null || referenceMolecules.isEmpty()) {
            throw new IllegalStateException("No reference molecules for identifiers to query ChEBI");
        }

        Map<String, GKInstance> chEBIIdentifierToReferenceMoleculeMap =
            Utils.getIdentifierToReferenceMoleculeMap(referenceMolecules);

        Set<String> chEBIIdentifiers = chEBIIdentifierToReferenceMoleculeMap.keySet();
        String restURL = "https://www.ebi.ac.uk/chebi/backend/api/public/compounds/";
        JSONObject chEBIResponseJSON = sendRequestToRESTfulAPI(restURL, getJSONPayload(chEBIIdentifiers));

        Map<GKInstance, Optional<ChEBIEntity>> chEBIEntities = new HashMap<>();
        for (String chEBIIdentifier : chEBIIdentifiers) {
            GKInstance referenceMolecule = chEBIIdentifierToReferenceMoleculeMap.get(chEBIIdentifier);

            JSONObject chEBIIdentifierJSON = chEBIResponseJSON.getJSONObject(chEBIIdentifier);
            if (chEBIIdentifierJSON.getBoolean("exists")) {
                chEBIEntities.put(referenceMolecule, Optional.of(
                    parseChEBIEntity(chEBIIdentifierJSON.getJSONObject("data")))
                );
            } else {
                chEBIEntities.put(referenceMolecule, Optional.empty());
            }
        }
        return chEBIEntities;
    }

    private JSONObject sendRequestToRESTfulAPI(String restURL, JSONObject chEBIIdentifiersPayload)
        throws IOException, InterruptedException {

        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(restURL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(chEBIIdentifiersPayload.toString()))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Unable to retrieve ChEBI entity/entities from RESTful API: " + response);
        }

        return new JSONObject(response.body());
    }

    private JSONObject getJSONPayload(Set<String> chEBIIdentifiers) {
        JSONArray chEBIIdentifiersArray = new JSONArray(chEBIIdentifiers);

        JSONObject payload = new JSONObject();
        payload.put("chebi_ids", chEBIIdentifiersArray);
        return payload;
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
