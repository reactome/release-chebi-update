package org.reactome.reports;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ReferenceMoleculeChEBIIdentifierChangeReporter implements Reportable {

    @Override
    public String getHeader() {
        return String.join("\t",
            "DB_ID",
            "Creator",
            "Reference Molecule",
            "Deprecated Identifier",
            "Replacement Identifier",
            "Affected referenceEntity DB_IDs",
            "DB_ID of Molecule with Replacement Identifier",
            "DB_IDs of referenceEntities of Molecule with Replacement Identifier"
        );
    }

    @Override
    public Path getFilePath() {
        return Paths.get("reference-molecule-chebi-identifier-changes.tsv");
    }
}
