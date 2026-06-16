package com.parsernews.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.model.ScanSummary;
import com.parsernews.service.NewsScannerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReportController {
    private final ObjectMapper objectMapper;
    private final NewsScannerService newsScannerService;
    private final Path jsonPath;
    private final Path csvPath;
    private final Path mismatchCsvPath;

    public ReportController(
            ObjectMapper objectMapper,
            NewsScannerService newsScannerService,
            @Value("${scanner.report-json:output/scan-results.json}") String jsonPath,
            @Value("${scanner.report-csv:output/scan-results.csv}") String csvPath,
            @Value("${scanner.mismatch-report-csv:output/mismatches.csv}") String mismatchCsvPath
    ) {
        this.objectMapper = objectMapper;
        this.newsScannerService = newsScannerService;
        this.jsonPath = Path.of(jsonPath);
        this.csvPath = Path.of(csvPath);
        this.mismatchCsvPath = Path.of(mismatchCsvPath);
    }

    @org.springframework.web.bind.annotation.PostMapping("/scan")
    public ScanSummary scan() {
        return newsScannerService.scan();
    }

    @GetMapping("/report")
    public ResponseEntity<JsonNode> report() throws IOException {
        if (!Files.exists(jsonPath)) {
            return ResponseEntity.ok(objectMapper.valueToTree(Map.of(
                    "summary", Map.of(),
                    "results", new Object[0]
            )));
        }
        return ResponseEntity.ok(objectMapper.readTree(jsonPath.toFile()));
    }

    @GetMapping("/report.csv")
    public ResponseEntity<Resource> reportCsv() {
        return csvResponse(csvPath, "scan-results.csv");
    }

    @GetMapping("/mismatches.csv")
    public ResponseEntity<Resource> mismatchesCsv() {
        return csvResponse(mismatchCsvPath, "mismatches.csv");
    }

    private ResponseEntity<Resource> csvResponse(Path path, String filename) {
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
