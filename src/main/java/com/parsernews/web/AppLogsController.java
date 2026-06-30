package com.parsernews.web;

import com.parsernews.service.InMemoryLogAppender;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
public class AppLogsController {

    @GetMapping("/api/admin/logs")
    public List<InMemoryLogAppender.LogEntry> getLogs(
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search
    ) {
        List<InMemoryLogAppender.LogEntry> all = InMemoryLogAppender.getEntries();
        return all.stream()
                .filter(e -> level == null || level.isBlank()
                        || e.level().equalsIgnoreCase(level))
                .filter(e -> search == null || search.isBlank()
                        || e.message().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))
                        || e.logger().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .skip(Math.max(0, all.size() - limit))
                .toList();
    }

    @DeleteMapping("/api/admin/logs")
    public ResponseEntity<Void> clearLogs() {
        InMemoryLogAppender.clear();
        return ResponseEntity.noContent().build();
    }
}
