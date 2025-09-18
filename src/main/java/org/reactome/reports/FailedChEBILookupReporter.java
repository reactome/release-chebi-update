package org.reactome.reports;

import java.nio.file.Path;
import java.nio.file.Paths;

public class FailedChEBILookupReporter implements Reportable {

    @Override
    public String getHeader() {
        return String.join("\t",
        "Reference Molecule DbId",
            "Reference Molecule Creator",
            "Reference Molecule Display Name"
        );
    }

    @Override
    public Path getFilePath() {
        return getReportDirectory().resolve(Paths.get("failed-chebi-lookups.tsv"));
    }
}
