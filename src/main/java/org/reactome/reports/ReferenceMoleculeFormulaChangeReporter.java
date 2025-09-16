package org.reactome.reports;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ReferenceMoleculeFormulaChangeReporter implements Reportable {
    @Override
    public String getHeader() {
        return String.join("\t",
            "Reference Molecule DbId",
            "Reference Molecule Creator",
            "Reference Molecule Display Name",
            "Old Formula",
            "New Formula"
        );
    }

    @Override
    public Path getFilePath() {
        return Paths.get("reference-molecule-formula-changes.tsv");
    }
}
