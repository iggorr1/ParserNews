package com.parsernews.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves the nightly discard-pile audit report to the dashboard. The audit (a diagnostic that runs
 * on the LLM host and re-checks articles the rules rejected) writes a JSON file into a directory
 * mounted read-only into this container; this endpoint just reads and returns it. When the file is
 * absent (audit hasn't run, or the mount is missing) it returns an empty, well-formed report so the
 * UI can render "no data" instead of erroring.
 */
@RestController
public class DiscardAuditController {
    private static final Logger log = LoggerFactory.getLogger(DiscardAuditController.class);

    private final ObjectMapper objectMapper;
    private final Path reportFile;

    public DiscardAuditController(
            ObjectMapper objectMapper,
            @Value("${audit.web-file:/app/audit/discard-audit.json}") String reportFile
    ) {
        this.objectMapper = objectMapper;
        this.reportFile = Path.of(reportFile);
    }

    @GetMapping("/api/discard-audit")
    public JsonNode discardAudit() {
        if (!Files.isReadable(reportFile)) {
            return empty("Audit report not available yet.");
        }
        try {
            JsonNode node = objectMapper.readTree(Files.readAllBytes(reportFile));
            return node == null || node.isNull() ? empty("Audit report is empty.") : node;
        } catch (Exception exception) {
            log.warn("Could not read discard-audit report {}: {}", reportFile, exception.getMessage());
            return empty("Audit report could not be read.");
        }
    }

    private ObjectNode empty(String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("available", false);
        node.put("message", message);
        node.putNull("generatedAt");
        node.putNull("date");
        node.put("scanned", 0);
        node.put("flagged", 0);
        node.put("publicHits", 0);
        node.set("hits", objectMapper.createArrayNode());
        node.set("history", objectMapper.createArrayNode());
        return node;
    }
}
