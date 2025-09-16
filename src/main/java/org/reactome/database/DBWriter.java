package org.reactome.database;

import org.gk.model.GKInstance;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;

public interface DBWriter {
    boolean updateSimpleEntityReferrersNames(GKInstance referenceMolecule, String newName) throws Exception;
    boolean updateReferenceMoleculeName(GKInstance referenceMolecule, String newName) throws Exception;
    boolean updateReferenceMoleculeFormula(GKInstance referenceMolecule, String newFormula) throws Exception;
    boolean updateReferenceMoleculeDisplayName(GKInstance referenceMolecule) throws Exception;
}
