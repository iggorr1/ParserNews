package com.parsernews.service;

import com.parsernews.config.ConsoleSettings;
import com.parsernews.model.AnalysisResult;
import com.parsernews.model.EventStatus;
import com.parsernews.model.EventType;
import com.parsernews.model.NewsEvent;
import com.parsernews.model.ScanSummary;
import com.parsernews.parser.NewsSourceParser;
import com.parsernews.persistence.NewsArticleRepository;
import com.parsernews.persistence.ScanRunEntity;
import com.parsernews.persistence.ScanRunRepository;
import com.parsernews.persistence.ScanRunStatus;
import com.parsernews.persistence.ScanRunTriggerType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

class NewsScannerServiceTest {
    @Test
    void skipsAlreadyStoredArticlesByUrlHash() {
        NewsEvent event = new NewsEvent(
                "TEST",
                "Test Company",
                "Test Company Enters Definitive Merger Agreement",
                "Shareholders will receive $5.00 per share in cash.",
                "Test Source",
                "https://example.com/already-stored"
        );
        NewsSourceParser parser = () -> List.of(event);
        RuleBasedNewsAnalyzer analyzer = mock(RuleBasedNewsAnalyzer.class);
        AlertService alertService = mock(AlertService.class);
        ReportExportService reportExportService = mock(ReportExportService.class);
        EventPersistenceService eventPersistenceService = mock(EventPersistenceService.class);
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        ScanRunRepository scanRunRepository = scanRunRepository();
        when(articleRepository.existsByUrlHash(EventPersistenceService.urlHash(event.sourceUrl()))).thenReturn(true);

        NewsScannerService service = new NewsScannerService(
                parser,
                analyzer,
                alertService,
                new DuplicateNewsFilter(),
                reportExportService,
                eventPersistenceService,
                articleRepository,
                scanRunRepository,
                new ConsoleSettings(EventStatus.IGNORED)
        );

        ScanSummary summary = service.scan();

        assertThat(summary.totalRead()).isEqualTo(1);
        assertThat(summary.analyzed()).isZero();
        assertThat(summary.duplicatesSkipped()).isEqualTo(1);
        verify(analyzer, never()).analyze(event);
        verify(eventPersistenceService, never()).save(event, null);
        ScanRunEntity scanRun = savedScanRun(scanRunRepository);
        assertThat(scanRun.getStatus()).isEqualTo(ScanRunStatus.SUCCESS);
        assertThat(scanRun.getTotalFetched()).isEqualTo(1);
        assertThat(scanRun.getSavedArticles()).isZero();
        assertThat(scanRun.getDuplicatesSkipped()).isEqualTo(1);
    }

    @Test
    void successfulManualScanCreatesScanRun() {
        NewsEvent event = new NewsEvent(
                "TEST",
                "Test Company",
                "Test Company Enters Definitive Merger Agreement",
                "Shareholders will receive $5.00 per share in cash.",
                "Test Source",
                "https://example.com/new"
        );
        NewsSourceParser parser = () -> List.of(event);
        RuleBasedNewsAnalyzer analyzer = mock(RuleBasedNewsAnalyzer.class);
        AnalysisResult result = new AnalysisResult(
                EventType.TAKE_PRIVATE_CONFIRMED,
                EventStatus.HIGH_PRIORITY_SIGNAL,
                80,
                List.of("definitive merger agreement"),
                List.of(),
                "Matched"
        );
        when(analyzer.analyze(event)).thenReturn(result);
        AlertService alertService = mock(AlertService.class);
        ReportExportService reportExportService = mock(ReportExportService.class);
        EventPersistenceService eventPersistenceService = mock(EventPersistenceService.class);
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        ScanRunRepository scanRunRepository = scanRunRepository();

        NewsScannerService service = new NewsScannerService(
                parser,
                analyzer,
                alertService,
                new DuplicateNewsFilter(),
                reportExportService,
                eventPersistenceService,
                articleRepository,
                scanRunRepository,
                new ConsoleSettings(EventStatus.HIGH_PRIORITY_SIGNAL)
        );

        ScanSummary summary = service.scan(ScanRunTriggerType.MANUAL);

        assertThat(summary.totalRead()).isEqualTo(1);
        assertThat(summary.analyzed()).isEqualTo(1);
        assertThat(summary.duplicatesSkipped()).isZero();
        verify(eventPersistenceService).save(event, result);
        verify(reportExportService).export(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(summary));
        ScanRunEntity scanRun = savedScanRun(scanRunRepository);
        assertThat(scanRun.getTriggerType()).isEqualTo(ScanRunTriggerType.MANUAL);
        assertThat(scanRun.getStatus()).isEqualTo(ScanRunStatus.SUCCESS);
        assertThat(scanRun.getTotalFetched()).isEqualTo(1);
        assertThat(scanRun.getCandidatesFound()).isEqualTo(1);
        assertThat(scanRun.getSavedArticles()).isEqualTo(1);
        assertThat(scanRun.getDuplicatesSkipped()).isZero();
        assertThat(scanRun.getErrorsCount()).isZero();
    }

    @Test
    void failedScanRecordsFailedScanRun() {
        RuntimeException failure = new RuntimeException("Feed failed");
        NewsSourceParser parser = () -> {
            throw failure;
        };
        RuleBasedNewsAnalyzer analyzer = mock(RuleBasedNewsAnalyzer.class);
        AlertService alertService = mock(AlertService.class);
        ReportExportService reportExportService = mock(ReportExportService.class);
        EventPersistenceService eventPersistenceService = mock(EventPersistenceService.class);
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        ScanRunRepository scanRunRepository = scanRunRepository();

        NewsScannerService service = new NewsScannerService(
                parser,
                analyzer,
                alertService,
                new DuplicateNewsFilter(),
                reportExportService,
                eventPersistenceService,
                articleRepository,
                scanRunRepository,
                new ConsoleSettings(EventStatus.IGNORED)
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.scan(ScanRunTriggerType.SCHEDULED))
                .isSameAs(failure);
        ScanRunEntity scanRun = savedScanRun(scanRunRepository);
        assertThat(scanRun.getTriggerType()).isEqualTo(ScanRunTriggerType.SCHEDULED);
        assertThat(scanRun.getStatus()).isEqualTo(ScanRunStatus.FAILED);
        assertThat(scanRun.getErrorsCount()).isEqualTo(1);
        assertThat(scanRun.getErrorMessage()).isEqualTo("Feed failed");
    }

    private ScanRunRepository scanRunRepository() {
        ScanRunRepository scanRunRepository = mock(ScanRunRepository.class);
        when(scanRunRepository.save(any(ScanRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return scanRunRepository;
    }

    private ScanRunEntity savedScanRun(ScanRunRepository scanRunRepository) {
        ArgumentCaptor<ScanRunEntity> captor = ArgumentCaptor.forClass(ScanRunEntity.class);
        verify(scanRunRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
