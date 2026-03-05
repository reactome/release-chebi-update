package org.reactome.reports;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SimpleEntityNameChangeReporter implements Reportable {

    @Override
    public String getHeader() {
        return String.join("\t",
            "Simple Entity DbId",
            "Simple Entity Creator",
            "Simple Entity Display Name",
            "New ChEBI Name",
            "Existing Simple Entity Names",
            "Updated Simple Entity Names",
            "Automatically Applied Update?"
        );
    }

    @Override
    public Path getFilePath() {
        return getReportDirectory().resolve(Paths.get("simple-entity-name-changes.tsv"));
    }
}
