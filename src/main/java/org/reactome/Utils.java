package org.reactome;

import org.gk.model.ReactomeJavaConstants;
import org.reactome.curation.model.SimpleInstance;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Utils {

    public static Map<String, SimpleInstance> getIdentifierToReferenceMoleculeMap(List<SimpleInstance> referenceMolecules) {
        return referenceMolecules
            .stream()
            .collect(Collectors.toMap(
                Utils::getReferenceMoleculeIdentifier,
                referenceMolecule -> referenceMolecule)
            );
    }

    private static String getReferenceMoleculeIdentifier(SimpleInstance referenceMolecule) {
        try {
            return (String) referenceMolecule.getAttribute(ReactomeJavaConstants.identifier);
        } catch (Exception e) {
            throw new RuntimeException("Unable to get reference molecule identifier for " + referenceMolecule, e);
        }
    }
}
