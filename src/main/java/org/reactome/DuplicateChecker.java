package org.reactome;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.ReactomeJavaConstants;
import org.reactome.curation.model.SimpleInstance;
import org.reactome.reports.DuplicateReferenceMoleculeReporter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.reactome.reports.Utils.getCreatorName;

public class DuplicateChecker {
    private static Logger logger = LogManager.getLogger(DuplicateChecker.class);

    private List<SimpleInstance> referenceMolecules;
    private DuplicateReferenceMoleculeReporter duplicateReferenceMoleculeReporter;

    public DuplicateChecker(List<SimpleInstance> referenceMolecules) {
        this.referenceMolecules = referenceMolecules;
        this.duplicateReferenceMoleculeReporter = new DuplicateReferenceMoleculeReporter();
    }

    public void findAndLogDuplicates() throws Exception {
        Map<String, List<SimpleInstance>> duplicates = getDuplicateIdentifierToReferenceMolecules();

        for (Map.Entry<String, List<SimpleInstance>> entry : duplicates.entrySet()) {
            String identifier = entry.getKey();
            List<SimpleInstance> duplicateReferenceMolecules = entry.getValue();

            // Log each duplicate instance
            for (SimpleInstance referenceMolecule : duplicateReferenceMolecules) {
                this.duplicateReferenceMoleculeReporter.report(
                    referenceMolecule.getDbId().toString(),
                    getCreatorName(referenceMolecule),
                    identifier,
                    referenceMolecule.getDisplayName()
                );
            }

            this.duplicateReferenceMoleculeReporter.writeFooterIfInitialized();
        }
    }

    private Map<String, List<SimpleInstance>> getDuplicateIdentifierToReferenceMolecules() {
        return referenceMolecules.stream()
            .collect(Collectors.groupingBy(molecule -> {
                try {
                    return (String) molecule.getAttribute(ReactomeJavaConstants.identifier);
                } catch (Exception e) {
                    logger.error("Error getting identifier for molecule: {}", molecule, e);
                    return "";
                }
            }))
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

}
