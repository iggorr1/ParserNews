package com.parsernews.service;

import com.parsernews.config.ConsoleSettings;
import com.parsernews.model.AnalysisResult;
import com.parsernews.model.EventStatus;
import com.parsernews.model.EventType;
import com.parsernews.model.NewsEvent;
import com.parsernews.model.ScanSummary;
import com.parsernews.parser.NewsSourceParser;
import com.parsernews.persistence.NewsArticleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(articleRepository.existsByUrlHash(EventPersistenceService.urlHash(event.sourceUrl()))).thenReturn(true);

        NewsScannerService service = new NewsScannerService(
                parser,
                analyzer,
                alertService,
                new DuplicateNewsFilter(),
                reportExportService,
                eventPersistenceService,
                articleRepository,
                new ConsoleSettings(EventStatus.IGNORED)
        );

        ScanSummary summary = service.scan();

        assertThat(summary.totalRead()).isEqualTo(1);
        assertThat(summary.analyzed()).isZero();
        assertThat(summary.duplicatesSkipped()).isEqualTo(1);
        verify(analyzer, never()).analyze(event);
        verify(eventPersistenceService, never()).save(event, null);
    }

    @Test
    void returnsSummaryForManualScanEndpoint() {
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

        NewsScannerService service = new NewsScannerService(
                parser,
                analyzer,
                alertService,
                new DuplicateNewsFilter(),
                reportExportService,
                eventPersistenceService,
                articleRepository,
                new ConsoleSettings(EventStatus.HIGH_PRIORITY_SIGNAL)
        );

        ScanSummary summary = service.scan();

        assertThat(summary.totalRead()).isEqualTo(1);
        assertThat(summary.analyzed()).isEqualTo(1);
        assertThat(summary.duplicatesSkipped()).isZero();
        verify(eventPersistenceService).save(event, result);
        verify(reportExportService).export(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(summary));
    }
}
