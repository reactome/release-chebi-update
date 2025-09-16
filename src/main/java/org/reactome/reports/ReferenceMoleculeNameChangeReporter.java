package org.reactome.reports;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ReferenceMoleculeNameChangeReporter implements Reportable {
    @Override
    public String getHeader() {
        return String.join("\t",
            "Reference Molecule DbId",
            "Reference Molecule Creator",
            "Reference Molecule Display Name",
            "Old Name",
            "New Name"
        );
    }

    @Override
    public Path getFilePath() {
        return Paths.get("reference-molecule-name-changes.tsv");
    }
}
