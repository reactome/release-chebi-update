package org.reactome.reports;

import java.nio.file.Path;
import java.nio.file.Paths;

public class DuplicateReferenceMoleculeReporter extends AbstractReporter {

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
    public String getFooter() {
        return "This report specifies Reference Molecule instances with duplicated ChEBI identifiers.  " +
            "Where possible, these should be merged and/or all but one removed from the curator database.";
    }

    @Override
    public Path getFilePath() {
        return getReportDirectory().resolve(Paths.get("duplicates.tsv"));
    }
}
