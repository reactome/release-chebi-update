package org.reactome.reports;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReporterTest {

    @TempDir
    Path tempDir;

    private TestReporter reporter;

    private static class TestReporter extends AbstractReporter {
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
        public String getFooter() {
            return "Example Footer";
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
        assertEquals("value1\tvalue2\tvalue3", lines.get(1));
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
    void testReportWithEmptyValues() throws IOException {
        reporter.report("");

        List<String> lines = Files.readAllLines(reporter.getFilePath());

        assertEquals(2, lines.size());
        assertEquals(reporter.getHeader(), lines.get(0));
        assertEquals("", lines.get(1).trim());
    }

    @Test
    void testReportWithNullValues() {
        assertThrows(NullPointerException.class, () -> reporter.report((String[]) null));
    }

    @Test
    void testWriteFooterIfRowsExist_noRows_footerNotWritten() throws IOException {
        reporter.writeFooterIfInitialized();

        assertFalse(Files.exists(reporter.getFilePath()));
    }

    @Test
    void testWriteFooterIfRowsExist_afterRows_footerWritten() throws IOException {
        reporter.report("value1", "value2", "value3");

        reporter.writeFooterIfInitialized();

        List<String> lines = Files.readAllLines(reporter.getFilePath());

        assertEquals(5, lines.size());
        assertEquals(reporter.getFooter(), lines.get(lines.size() - 1));
    }
}