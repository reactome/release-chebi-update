package org.reactome.database;

import org.gk.model.GKInstance;

import java.util.List;

public interface DBReader {
    List<GKInstance> getAllChEBIReferenceMoleculeInstances() throws Exception;

    List<GKInstance> getReferenceMoleculesWithChEBIIdentifier(String chEBIId) throws Exception;
}
