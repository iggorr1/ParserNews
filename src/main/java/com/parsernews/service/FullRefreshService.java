package com.parsernews.service;

import com.parsernews.model.ScanSummary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class FullRefreshService {
    private final NewsScannerService newsScannerService;
    private final SecWatchlistScanner secWatchlistScanner;
    private final SecFilingDocumentFetcher secFilingDocumentFetcher;
    private final CandidateRecomputeService candidateRecomputeService;

    public FullRefreshService(
            NewsScannerService newsScannerService,
            SecWatchlistScanner secWatchlistScanner,
            SecFilingDocumentFetcher secFilingDocumentFetcher,
            CandidateRecomputeService candidateRecomputeService
    ) {
        this.newsScannerService = newsScannerService;
        this.secWatchlistScanner = secWatchlistScanner;
        this.secFilingDocumentFetcher = secFilingDocumentFetcher;
        this.candidateRecomputeService = candidateRecomputeService;
    }

    public FullRefreshSummary fullRefresh() {
        Instant startedAt = Instant.now();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        ScanSummary rssScanSummary = null;
        SecWatchlistScanner.SecScanSummary secScanSummary = null;
        SecFilingDocumentFetcher.SecDocumentFetchSummary secDocumentFetchSummary = null;
        CandidateRecomputeService.RecomputeSummary recomputeSummary = null;

        try {
            rssScanSummary = newsScannerService.scan();
        } catch (RuntimeException exception) {
            errors.add("RSS scan failed: " + safeMessage(exception));
        }

        try {
            secScanSummary = secWatchlistScanner.scan();
            if (!secScanSummary.enabled() || secScanSummary.watchlistCount() == 0) {
                warnings.add("SEC scanner disabled or watchlist empty");
            }
            if (secScanSummary.message() != null && !secScanSummary.message().isBlank()
                    && !secScanSummary.message().equals("SEC scan completed.")) {
                warnings.add(secScanSummary.message());
            }
        } catch (RuntimeException exception) {
            errors.add("SEC scan failed: " + safeMessage(exception));
        }

        try {
            secDocumentFetchSummary = secFilingDocumentFetcher.fetchPendingDocuments();
        } catch (RuntimeException exception) {
            errors.add("SEC document fetch failed: " + safeMessage(exception));
        }

        try {
            recomputeSummary = candidateRecomputeService.recomputeCandidates();
        } catch (RuntimeException exception) {
            errors.add("RSS candidate recompute failed: " + safeMessage(exception));
        }

        return new FullRefreshSummary(
                startedAt,
                Instant.now(),
                rssScanSummary,
                secScanSummary,
                secDocumentFetchSummary,
                recomputeSummary,
                warnings,
                errors,
                errors.isEmpty()
        );
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    public record FullRefreshSummary(
            Instant startedAt,
            Instant finishedAt,
            ScanSummary rssScanSummary,
            SecWatchlistScanner.SecScanSummary secScanSummary,
            SecFilingDocumentFetcher.SecDocumentFetchSummary secDocumentFetchSummary,
            CandidateRecomputeService.RecomputeSummary recomputeSummary,
            List<String> warnings,
            List<String> errors,
            boolean success
    ) {
    }
}
