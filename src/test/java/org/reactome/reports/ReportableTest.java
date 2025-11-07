package org.reactome.reports;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportableTest {

    @TempDir
    Path tempDir;

    private TestReporter reporter;

    // Test implementation of Reportable interface
    private static class TestReporter implements Reportable {
        private final Path reportDirectory;
        private final String fileName;

        public TestReporter(Path reportDirectory, String fileName) {
            this.reportDirectory = reportDirectory;
            this.fileName = fileName;
        }

        @Override
        public String getHeader() {
            return "Column1\tColumn2\tColumn3";
        }

        @Override
        public Path getFilePath() {
            return reportDirectory.resolve(fileName);
        }

        @Override
        public Path getReportDirectory() {
            return reportDirectory;
        }
    }

    @BeforeEach
    void setUp() {
        reporter = new TestReporter(tempDir, "test-report.tsv");
    }

    @Test
    void testReportCreatesDirectoryIfNotExists() throws IOException {
        Path customDir = tempDir.resolve("custom-reports");
        TestReporter customReporter = new TestReporter(customDir, "test.tsv");

        assertFalse(Files.exists(customDir));
        customReporter.report("value1", "value2", "value3");
        assertTrue(Files.exists(customDir));
    }

    @Test
    void testReportWritesHeaderOnFirstWrite() throws IOException {
        reporter.report("value1", "value2", "value3");

        List<String> lines = Files.readAllLines(reporter.getFilePath());
        assertEquals(2, lines.size());
        assertEquals(reporter.getHeader(), lines.get(0));
    }

    @Test
    void testReportAppendsDataWithoutHeaderOnSubsequentWrites() throws IOException {
        reporter.report("first1", "first2", "first3");
        reporter.report("second1", "second2", "second3");

        List<String> lines = Files.readAllLines(reporter.getFilePath());
        assertEquals(3, lines.size());
        assertEquals(reporter.getHeader(), lines.get(0));
        assertEquals("first1\tfirst2\tfirst3", lines.get(1));
        assertEquals("second1\tsecond2\tsecond3", lines.get(2));
    }

    @Test
    void testWriteHeaderCreatesFile() throws IOException {
        assertFalse(Files.exists(reporter.getFilePath()));
        reporter.writeHeader();
        assertTrue(Files.exists(reporter.getFilePath()));
    }

    @Test
    void testWriteHeaderWritesCorrectContent() throws IOException {
        reporter.writeHeader();
        List<String> lines = Files.readAllLines(reporter.getFilePath());
        assertEquals(1, lines.size());
        assertEquals(reporter.getHeader(), lines.get(0));
    }

    @Test
    void testReportWithEmptyValues() throws IOException {
        reporter.report("");

        List<String> lines = Files.readAllLines(reporter.getFilePath());
        assertEquals(2, lines.size());
        assertEquals("", lines.get(1).trim());
    }

    @Test
    void testReportWithNullValues() {
        assertThrows(NullPointerException.class, () -> reporter.report((String[]) null));
    }
}