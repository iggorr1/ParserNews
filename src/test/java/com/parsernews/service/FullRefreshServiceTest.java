package com.parsernews.service;

import com.parsernews.model.ScanSummary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FullRefreshServiceTest {
    private final NewsScannerService newsScannerService = mock(NewsScannerService.class);
    private final SecWatchlistScanner secWatchlistScanner = mock(SecWatchlistScanner.class);
    private final SecFilingDocumentFetcher documentFetcher = mock(SecFilingDocumentFetcher.class);
    private final CandidateRecomputeService recomputeService = mock(CandidateRecomputeService.class);
    private final FullRefreshService service = new FullRefreshService(
            newsScannerService,
            secWatchlistScanner,
            documentFetcher,
            recomputeService
    );

    @Test
    void fullRefreshReturnsSuccessWhenStepsSucceed() {
        when(newsScannerService.scan()).thenReturn(new ScanSummary(10, 8, 2, 0, 0, 0));
        when(secWatchlistScanner.scan()).thenReturn(new SecWatchlistScanner.SecScanSummary(true, 2, 20, 4, 3, 1, "SEC scan completed."));
        when(documentFetcher.fetchPendingDocuments()).thenReturn(new SecFilingDocumentFetcher.SecDocumentFetchSummary(3, 2, 0, 1));
        when(recomputeService.recomputeCandidates()).thenReturn(new CandidateRecomputeService.RecomputeSummary(5, 2, 1, 1, 1, 2, 1, 0));

        FullRefreshService.FullRefreshSummary summary = service.fullRefresh();

        assertThat(summary.success()).isTrue();
        assertThat(summary.startedAt()).isNotNull();
        assertThat(summary.finishedAt()).isNotNull();
        assertThat(summary.rssScanSummary().totalRead()).isEqualTo(10);
        assertThat(summary.secScanSummary().savedFilings()).isEqualTo(3);
        assertThat(summary.secDocumentFetchSummary().fetchedCount()).isEqualTo(2);
        assertThat(summary.recomputeSummary().updatedEvents()).isEqualTo(2);
        assertThat(summary.warnings()).isEmpty();
        assertThat(summary.errors()).isEmpty();

        verify(newsScannerService).scan();
        verify(secWatchlistScanner).scan();
        verify(documentFetcher).fetchPendingDocuments();
        verify(recomputeService).recomputeCandidates();
    }

    @Test
    void secDisabledReturnsWarningAndContinues() {
        when(newsScannerService.scan()).thenReturn(new ScanSummary(1, 1, 0, 0, 0, 0));
        when(secWatchlistScanner.scan()).thenReturn(new SecWatchlistScanner.SecScanSummary(false, 0, 0, 0, 0, 0, "SEC scanner is disabled."));
        when(documentFetcher.fetchPendingDocuments()).thenReturn(new SecFilingDocumentFetcher.SecDocumentFetchSummary(0, 0, 0, 0));
        when(recomputeService.recomputeCandidates()).thenReturn(new CandidateRecomputeService.RecomputeSummary(1, 1, 1, 0, 0, 0, 1, 0));

        FullRefreshService.FullRefreshSummary summary = service.fullRefresh();

        assertThat(summary.success()).isTrue();
        assertThat(summary.warnings()).contains("SEC scanner disabled or watchlist empty");
        assertThat(summary.warnings()).contains("SEC scanner is disabled.");
        assertThat(summary.rssScanSummary()).isNotNull();
        assertThat(summary.recomputeSummary()).isNotNull();
    }

    @Test
    void stepFailureIsCapturedAndRemainingStepsContinue() {
        when(newsScannerService.scan()).thenThrow(new IllegalStateException("RSS failed"));
        when(secWatchlistScanner.scan()).thenReturn(new SecWatchlistScanner.SecScanSummary(true, 1, 3, 1, 1, 0, "SEC scan completed."));
        when(documentFetcher.fetchPendingDocuments()).thenReturn(new SecFilingDocumentFetcher.SecDocumentFetchSummary(1, 1, 0, 0));
        when(recomputeService.recomputeCandidates()).thenReturn(new CandidateRecomputeService.RecomputeSummary(1, 0, 1, 0, 0, 0, 1, 0));

        FullRefreshService.FullRefreshSummary summary = service.fullRefresh();

        assertThat(summary.success()).isFalse();
        assertThat(summary.errors()).contains("RSS scan failed: RSS failed");
        assertThat(summary.secScanSummary()).isNotNull();
        assertThat(summary.secDocumentFetchSummary()).isNotNull();
        assertThat(summary.recomputeSummary()).isNotNull();
    }
}
