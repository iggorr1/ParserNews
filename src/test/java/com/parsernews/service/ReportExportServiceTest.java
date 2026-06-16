package com.parsernews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.model.AnalysisResult;
import com.parsernews.model.EventStatus;
import com.parsernews.model.EventType;
import com.parsernews.model.NewsEvent;
import com.parsernews.model.ScanResult;
import com.parsernews.model.ScanSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExportServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void writesJsonAndCsvReports() throws Exception {
        Path jsonPath = tempDir.resolve("scan-results.json");
        Path csvPath = tempDir.resolve("scan-results.csv");
        ReportExportService service = new ReportExportService(
                new ObjectMapper(),
                jsonPath.toString(),
                csvPath.toString(),
                tempDir.resolve("mismatches.csv").toString()
        );

        ScanResult result = ScanResult.from(
                new NewsEvent(
                        "TEST",
                        "Test Company",
                        "Company Enters Definitive Merger Agreement",
                        "Shareholders will receive $5.00 per share in cash.",
                        "Test Source",
                        "https://example.com/test",
                        EventType.TAKE_PRIVATE_CONFIRMED,
                        EventStatus.IMPORTANT,
                        "Expected to match"
                ),
                new AnalysisResult(
                        EventType.TAKE_PRIVATE_CONFIRMED,
                        EventStatus.IMPORTANT,
                        80,
                        List.of("definitive merger agreement"),
                        List.of(),
                        "Matched test"
                ),
                true
        );

        service.export(List.of(result), new ScanSummary(1, 1, 0, 1, 1, 0));

        assertThat(jsonPath).exists();
        assertThat(csvPath).exists();
        assertThat(Files.readString(jsonPath)).contains("\"ticker\" : \"TEST\"");
        assertThat(Files.readString(csvPath)).contains("\"TEST\"");
    }

    @Test
    void writesOnlyFalseMatchesToMismatchReport() throws Exception {
        Path mismatchPath = tempDir.resolve("mismatches.csv");
        ReportExportService service = new ReportExportService(
                new ObjectMapper(),
                tempDir.resolve("scan-results.json").toString(),
                tempDir.resolve("scan-results.csv").toString(),
                mismatchPath.toString()
        );

        ScanResult match = result("GOOD", true);
        ScanResult mismatch = result("BAD", false);

        service.export(List.of(match, mismatch), new ScanSummary(2, 2, 0, 2, 1, 1));

        String csv = Files.readString(mismatchPath);
        assertThat(csv).contains("\"BAD\"");
        assertThat(csv).doesNotContain("\"GOOD\"");
    }

    private ScanResult result(String ticker, boolean matchesExpected) {
        return ScanResult.from(
                new NewsEvent(
                        ticker,
                        "Test Company",
                        "Company Enters Definitive Merger Agreement",
                        "Shareholders will receive $5.00 per share in cash.",
                        "Test Source",
                        "https://example.com/" + ticker,
                        EventType.TAKE_PRIVATE_CONFIRMED,
                        EventStatus.IMPORTANT,
                        "Expected label"
                ),
                new AnalysisResult(
                        EventType.TAKE_PRIVATE_CONFIRMED,
                        matchesExpected ? EventStatus.IMPORTANT : EventStatus.WATCHLIST,
                        80,
                        List.of("definitive merger agreement"),
                        List.of(),
                        "Matched test"
                ),
                matchesExpected
        );
    }
}
