package org.reactome.reports;

import java.nio.file.Path;
import java.nio.file.Paths;

public class DuplicateReferenceMoleculeReporter implements Reportable {

    @Override
    public String getHeader() {
        return String.join("\t",
            "Reference Molecule DbId",
            "Reference Molecule Creator",
            "Duplicated Identifier",
            "Reference Molecule Display Name"
        );
    }

    @Override
    public Path getFilePath() {
        return Paths.get("duplicates.tsv");
    }
}
