package org.reactome.webservice;

import org.gk.model.GKInstance;
import org.json.JSONObject;
import org.reactome.Utils;
import org.reactome.model.ChEBIEntity;
import org.reactome.webservice.helpers.ChEBIAPIClient;
import org.reactome.webservice.helpers.ChEBIEntityParser;

import java.io.IOException;
import java.util.*;

public class ChEBIEntityRetriever {
    private final ChEBIAPIClient chEBIAPIClient;
    private final ChEBIEntityParser chEBIEntityParser;

    public ChEBIEntityRetriever() {
        this(new ChEBIAPIClient(), new ChEBIEntityParser());
    }

    ChEBIEntityRetriever(ChEBIAPIClient chEBIAPIClient, ChEBIEntityParser chEBIEntityParser) {
        this.chEBIAPIClient = chEBIAPIClient;
        this.chEBIEntityParser = chEBIEntityParser;
    }

    public Map<GKInstance, Optional<ChEBIEntity>> getChEBIEntities(List<GKInstance> referenceMolecules)
        throws IOException, InterruptedException {

        if (referenceMolecules == null || referenceMolecules.isEmpty()) {
            throw new IllegalStateException("No reference molecules for identifiers to query ChEBI");
        }

        Map<String, GKInstance> chEBIIdentifierToReferenceMoleculeMap =
            Utils.getIdentifierToReferenceMoleculeMap(referenceMolecules);

        Set<String> chEBIIdentifiers = chEBIIdentifierToReferenceMoleculeMap.keySet();
        JSONObject chEBIResponseJSON = chEBIAPIClient.fetchCompounds(chEBIIdentifiers);

        Map<GKInstance, Optional<ChEBIEntity>> chEBIEntities = new HashMap<>();
        for (String chEBIIdentifier : chEBIIdentifiers) {
            GKInstance referenceMolecule = chEBIIdentifierToReferenceMoleculeMap.get(chEBIIdentifier);

            JSONObject chEBIIdentifierJSON = chEBIResponseJSON.getJSONObject(chEBIIdentifier);
            if (chEBIIdentifierJSON.getBoolean("exists")) {
                chEBIEntities.put(referenceMolecule, Optional.of(
                    chEBIEntityParser.parse(chEBIIdentifierJSON.getJSONObject("data"))
                ));
            } else {
                chEBIEntities.put(referenceMolecule, Optional.empty());
            }
        }
        return chEBIEntities;
    }
}
