package org.reactome.database;

import org.reactome.curation.model.SimpleInstance;

import java.util.List;

public interface DBReader {
    List<SimpleInstance> getAllChEBIReferenceMoleculeInstances() throws Exception;

    List<SimpleInstance> getReferenceMoleculesWithChEBIIdentifier(String chEBIId) throws Exception;

    List<SimpleInstance> getReferrerInstances(SimpleInstance instance, String attribute) throws Exception;
}
