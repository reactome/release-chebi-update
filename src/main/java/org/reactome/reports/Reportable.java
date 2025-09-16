package org.reactome.reports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public interface Reportable {

    String getHeader();

    Path getFilePath();

    default void report(String ...values) throws IOException {
        if (Files.notExists(getFilePath())) {
            writeHeader();
        }

        String reportLine = String.join("\t", values).concat(System.lineSeparator());
        Files.write(
            getFilePath(),
            reportLine.getBytes(),
            StandardOpenOption.APPEND
        );
    }

    default void writeHeader() throws IOException {
        Files.write(
            getFilePath(),
            getHeader().concat(System.lineSeparator()).getBytes(),
            StandardOpenOption.CREATE, StandardOpenOption.APPEND
        );
    }
}
