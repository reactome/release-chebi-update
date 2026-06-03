package org.reactome.reports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public abstract class AbstractReporter {

    private boolean initialized;

    public abstract String getHeader();

    public abstract String getFooter();

    public abstract Path getFilePath();

    public Path getReportDirectory() {
        return Paths.get("reports");
    }

    public void report(String ...values) throws IOException {
        initializeIfNeeded();

        String reportLine = String.join("\t", values).concat(System.lineSeparator());
        Files.write(
            getFilePath(),
            reportLine.getBytes(),
            StandardOpenOption.APPEND
        );
    }

    public void writeFooterIfInitialized() throws IOException {
        if (!initialized) {
            return;
        }

        String footerWithPrependedNewLines = ""
            .concat(System.lineSeparator())
            .concat(System.lineSeparator())
            .concat(getFooter());

        Files.write(
            getFilePath(),
            footerWithPrependedNewLines.getBytes(),
            StandardOpenOption.APPEND
        );
    }

    private void writeHeader() throws IOException {
        Files.write(
            getFilePath(),
            getHeader().concat(System.lineSeparator()).getBytes(),
            StandardOpenOption.CREATE, StandardOpenOption.APPEND
        );
    }

    private void initializeIfNeeded() throws IOException {
        if (initialized) {
            return;
        }

        if (Files.notExists(getReportDirectory())) {
            Files.createDirectories(getReportDirectory());
        }

        if (Files.notExists(getFilePath())) {
            writeHeader();
        }

        initialized = true;
    }
}
