package org.reactome.reports;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ReferenceMoleculeNameChangeReporter extends AbstractReporter {
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
    public String getFooter() {
        return "This report specifies when a Reference Molecule instance has a new name. " +
            "This report is only informational as the changes have already been applied in the database - " +
            " nothing need be done.";
    }

    @Override
    public Path getFilePath() {
        return getReportDirectory().resolve(Paths.get("reference-molecule-name-changes.tsv"));
    }
}
