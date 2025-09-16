package org.reactome;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Utils {

    public static Map<String, GKInstance> getIdentifierToReferenceMoleculeMap(List<GKInstance> referenceMolecules) {
        return referenceMolecules
            .stream()
            .collect(Collectors.toMap(
                Utils::getReferenceMoleculeIdentifier,
                referenceMolecule -> referenceMolecule)
            );
    }

    private static String getReferenceMoleculeIdentifier(GKInstance referenceMolecule) {
        try {
            return (String) referenceMolecule.getAttributeValue(ReactomeJavaConstants.identifier);
        } catch (Exception e) {
            throw new RuntimeException("Unable to get reference molecule identifier for " + referenceMolecule, e);
        }
    }
}
