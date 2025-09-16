package org.reactome;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.reactome.reports.DuplicateReferenceMoleculeReporter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.reactome.reports.Utils.getCreator;
import static org.reactome.reports.Utils.getCreatorName;

public class DuplicateChecker {
    private static Logger logger = LogManager.getLogger(DuplicateChecker.class);

    private List<GKInstance> referenceMolecules;
    private DuplicateReferenceMoleculeReporter duplicateReferenceMoleculeReporter;

    public DuplicateChecker(List<GKInstance> referenceMolecules) {
        this.referenceMolecules = referenceMolecules;
        this.duplicateReferenceMoleculeReporter = new DuplicateReferenceMoleculeReporter();
    }

    public void findAndLogDuplicates() throws Exception {
        Map<String, List<GKInstance>> duplicates = getDuplicateIdentifierToReferenceMolecules();

        for (Map.Entry<String, List<GKInstance>> entry : duplicates.entrySet()) {
            String identifier = entry.getKey();
            List<GKInstance> duplicateReferenceMolecules = entry.getValue();

            // Log each duplicate instance
            for (GKInstance referenceMolecule : duplicateReferenceMolecules) {
                GKInstance creator = getCreator(referenceMolecule);
                this.duplicateReferenceMoleculeReporter.report(
                    referenceMolecule.getDBID().toString(),
                    getCreatorName(creator),
                    identifier,
                    referenceMolecule.getDisplayName()
                );
            }
        }
    }

    private Map<String, List<GKInstance>> getDuplicateIdentifierToReferenceMolecules() {
        return referenceMolecules.stream()
            .collect(Collectors.groupingBy(molecule -> {
                try {
                    return (String) molecule.getAttributeValue(ReactomeJavaConstants.identifier);
                } catch (Exception e) {
                    logger.error("Error getting identifier for molecule: " + molecule, e);
                    return "";
                }
            }))
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

}
