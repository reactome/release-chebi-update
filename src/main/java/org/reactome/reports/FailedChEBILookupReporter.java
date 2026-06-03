package org.reactome.reports;

import java.nio.file.Path;
import java.nio.file.Paths;

public class FailedChEBILookupReporter extends AbstractReporter {

    @Override
    public String getHeader() {
        return String.join("\t",
        "Reference Molecule DbId",
            "Reference Molecule Creator",
            "Reference Molecule Display Name"
        );
    }

    @Override
    public String getFooter() {
        return "This report specifies Reference Molecule instances whose ChEBI identifier couldn't be retrieved " +
            "from the external ChEBI API.  This may have been due to an intermittent service interruption or an " +
            "invalid ChEBI identifier.  The identifiers reported should be checked manually in ChEBI to verify them.";
    }

    @Override
    public Path getFilePath() {
        return getReportDirectory().resolve(Paths.get("failed-chebi-lookups.tsv"));
    }
}
