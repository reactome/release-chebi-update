package org.reactome.reports;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SimpleEntityNameChangeReporter extends AbstractReporter {

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
    public String getFooter() {
        return "This report specifies Simple Entities with updated names.  If the update is applied automatically " +
            "(true for the last column), nothing need be done.  If the update is not applied automatically, it is " +
            "a suggestion for curators to apply what is given in the 'Update Simple Entity Names' column.";
    }

    @Override
    public Path getFilePath() {
        return getReportDirectory().resolve(Paths.get("simple-entity-name-changes.tsv"));
    }
}
