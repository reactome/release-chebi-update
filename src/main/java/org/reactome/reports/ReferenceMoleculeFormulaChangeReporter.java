package org.reactome.reports;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ReferenceMoleculeFormulaChangeReporter extends AbstractReporter {
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
    public String getFooter() {
        return "This report specifies when a Reference Molecule instance has a new chemical formula. " +
            "This report is only informational as the changes have already been applied in the database - " +
            " nothing need be done.";
    }

    @Override
    public Path getFilePath() {
        return getReportDirectory().resolve(Paths.get("reference-molecule-formula-changes.tsv"));
    }
}
