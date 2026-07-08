package org.reactome.database;

import org.reactome.curation.model.SimpleInstance;

public interface DBWriter {
    boolean updateSimpleEntityReferrersNames(SimpleInstance referenceMolecule, String newName) throws Exception;
    boolean stageUpdateForReferenceMoleculeName(SimpleInstance referenceMolecule, String newName) throws Exception;
    boolean stageUpdateForReferenceMoleculeFormula(SimpleInstance referenceMolecule, String newFormula) throws Exception;
    void stageUpdateForReferenceMoleculeDisplayName(SimpleInstance referenceMolecule) throws Exception;
    void updateInDb(SimpleInstance instance);
}
