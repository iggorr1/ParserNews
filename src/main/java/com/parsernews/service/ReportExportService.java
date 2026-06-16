package com.parsernews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.parsernews.model.ScanResult;
import com.parsernews.model.ScanSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

@Service
public class ReportExportService {
    private final ObjectMapper objectMapper;
    private final Path jsonPath;
    private final Path csvPath;
    private final Path mismatchCsvPath;

    public ReportExportService(
            ObjectMapper objectMapper,
            @Value("${scanner.report-json:output/scan-results.json}") String jsonPath,
            @Value("${scanner.report-csv:output/scan-results.csv}") String csvPath,
            @Value("${scanner.mismatch-report-csv:output/mismatches.csv}") String mismatchCsvPath
    ) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.jsonPath = Path.of(jsonPath);
        this.csvPath = Path.of(csvPath);
        this.mismatchCsvPath = Path.of(mismatchCsvPath);
    }

    public void export(List<ScanResult> results, ScanSummary summary) {
        writeJson(new ScanReport(summary, results));
        writeCsv(results);
        writeCsv(mismatchesOnly(results), mismatchCsvPath);
        System.out.println("Report JSON: " + jsonPath.toAbsolutePath());
        System.out.println("Report CSV: " + csvPath.toAbsolutePath());
        System.out.println("Mismatch CSV: " + mismatchCsvPath.toAbsolutePath());
    }

    private void writeJson(ScanReport report) {
        try {
            createParentDirectories(jsonPath);
            objectMapper.writeValue(jsonPath.toFile(), report);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write JSON report to " + jsonPath, exception);
        }
    }

    private void writeCsv(List<ScanResult> results) {
        writeCsv(results, csvPath);
    }

    private void writeCsv(List<ScanResult> results, Path path) {
        try {
            createParentDirectories(path);
            StringBuilder csv = new StringBuilder();
            csv.append(String.join(",",
                    "ticker",
                    "companyName",
                    "headline",
                    "source",
                    "eventType",
                    "status",
                    "score",
                    "offerPrice",
                    "cashOrStock",
                    "acquirer",
                    "premiumPercent",
                    "expectedEventType",
                    "expectedStatus",
                    "matchesExpected",
                    "positiveKeywords",
                    "negativeKeywords",
                    "sourceUrl",
                    "notes",
                    "reason"
            )).append(System.lineSeparator());

            for (ScanResult result : results) {
                csv.append(toCsvLine(result)).append(System.lineSeparator());
            }

            Files.writeString(path, csv.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write CSV report to " + path, exception);
        }
    }

    private List<ScanResult> mismatchesOnly(List<ScanResult> results) {
        return results.stream()
                .filter(result -> Boolean.FALSE.equals(result.matchesExpected()))
                .toList();
    }

    private String toCsvLine(ScanResult result) {
        StringJoiner joiner = new StringJoiner(",");
        joiner.add(csv(result.ticker()));
        joiner.add(csv(result.companyName()));
        joiner.add(csv(result.headline()));
        joiner.add(csv(result.source()));
        joiner.add(csv(result.eventType()));
        joiner.add(csv(result.status()));
        joiner.add(csv(result.score()));
        joiner.add(csv(result.offerPrice()));
        joiner.add(csv(result.cashOrStock()));
        joiner.add(csv(result.acquirer()));
        joiner.add(csv(result.premiumPercent()));
        joiner.add(csv(result.expectedEventType()));
        joiner.add(csv(result.expectedStatus()));
        joiner.add(csv(result.matchesExpected()));
        joiner.add(csv(String.join("|", result.matchedPositiveKeywords())));
        joiner.add(csv(String.join("|", result.matchedNegativeKeywords())));
        joiner.add(csv(result.sourceUrl()));
        joiner.add(csv(result.notes()));
        joiner.add(csv(result.reason()));
        return joiner.toString();
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private void createParentDirectories(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private record ScanReport(
            ScanSummary summary,
            List<ScanResult> results
    ) {
    }
}
