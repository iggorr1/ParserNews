package com.parsernews.web;

import com.parsernews.persistence.ScanRunEntity;
import com.parsernews.persistence.ScanRunRepository;
import com.parsernews.persistence.ScanRunStatus;
import com.parsernews.persistence.ScanRunTriggerType;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
public class ScanRunController {
    private final ScanRunRepository scanRunRepository;

    public ScanRunController(ScanRunRepository scanRunRepository) {
        this.scanRunRepository = scanRunRepository;
    }

    @GetMapping("/api/scan-runs")
    @Transactional(readOnly = true)
    public List<ScanRunResponse> scanRuns() {
        return scanRunRepository.findTop100ByOrderByStartedAtDesc().stream()
                .map(ScanRunResponse::from)
                .toList();
    }

    @GetMapping("/api/scan-runs/{id}")
    @Transactional(readOnly = true)
    public ScanRunResponse scanRun(@PathVariable Long id) {
        return scanRunRepository.findById(id)
                .map(ScanRunResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan run not found"));
    }

    public record ScanRunResponse(
            Long id,
            Instant startedAt,
            Instant finishedAt,
            ScanRunTriggerType triggerType,
            ScanRunStatus status,
            int totalFetched,
            int candidatesFound,
            int savedArticles,
            int duplicatesSkipped,
            int errorsCount,
            String errorMessage
    ) {
        static ScanRunResponse from(ScanRunEntity scanRun) {
            return new ScanRunResponse(
                    scanRun.getId(),
                    scanRun.getStartedAt(),
                    scanRun.getFinishedAt(),
                    scanRun.getTriggerType(),
                    scanRun.getStatus(),
                    scanRun.getTotalFetched(),
                    scanRun.getCandidatesFound(),
                    scanRun.getSavedArticles(),
                    scanRun.getDuplicatesSkipped(),
                    scanRun.getErrorsCount(),
                    scanRun.getErrorMessage()
            );
        }
    }
}
