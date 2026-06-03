package org.reactome.reports;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ReferenceMoleculeChEBIIdentifierChangeReporter extends AbstractReporter {

    @Override
    public String getHeader() {
        return String.join("\t",
            "DB_ID",
            "Creator",
            "Reference Molecule",
            "Deprecated Identifier",
            "Replacement Identifier",
            "Affected Simple Entity DB_IDs",
            "DB_ID of Reference Molecule with Replacement Identifier",
            "DB_IDs of Simple Entities of Reference Molecule with Replacement Identifier"
        );
    }

    @Override
    public String getFooter() {
        return "This reports specifies Reference Molecule instances whose ChEBI identifier has changed. " +
            "For any instances reported, it is necessary to move the SimpleEntity referrers to the new " +
            "Reference Molecule with the replacement identifier and remove the old Reference Molecule with " +
            "deprecated identifier";
    }

    @Override
    public Path getFilePath() {
        return getReportDirectory().resolve(Paths.get("reference-molecule-chebi-identifier-changes.tsv"));
    }
}
