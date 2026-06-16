package com.parsernews.service;

import com.parsernews.config.ConsoleSettings;
import com.parsernews.model.AnalysisResult;
import com.parsernews.model.EventStatus;
import com.parsernews.model.NewsEvent;
import com.parsernews.model.ScanResult;
import com.parsernews.model.ScanSummary;
import com.parsernews.parser.NewsSourceParser;
import com.parsernews.persistence.NewsArticleRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NewsScannerService {
    private final NewsSourceParser newsSourceParser;
    private final RuleBasedNewsAnalyzer analyzer;
    private final AlertService alertService;
    private final DuplicateNewsFilter duplicateNewsFilter;
    private final ReportExportService reportExportService;
    private final EventPersistenceService eventPersistenceService;
    private final NewsArticleRepository articleRepository;
    private final ConsoleSettings consoleSettings;

    public NewsScannerService(
            NewsSourceParser newsSourceParser,
            RuleBasedNewsAnalyzer analyzer,
            AlertService alertService,
            DuplicateNewsFilter duplicateNewsFilter,
            ReportExportService reportExportService,
            EventPersistenceService eventPersistenceService,
            NewsArticleRepository articleRepository,
            ConsoleSettings consoleSettings
    ) {
        this.newsSourceParser = newsSourceParser;
        this.analyzer = analyzer;
        this.alertService = alertService;
        this.duplicateNewsFilter = duplicateNewsFilter;
        this.reportExportService = reportExportService;
        this.eventPersistenceService = eventPersistenceService;
        this.articleRepository = articleRepository;
        this.consoleSettings = consoleSettings;
    }

    public synchronized ScanSummary scan() {
        List<NewsEvent> events = newsSourceParser.readNews();
        List<ScanResult> results = new ArrayList<>();
        int analyzed = 0;
        int duplicatesSkipped = 0;
        int labeled = 0;
        int matchedExpected = 0;
        int mismatchedExpected = 0;

        for (NewsEvent event : events) {
            if (isDuplicate(event)) {
                duplicatesSkipped++;
                continue;
            }
            AnalysisResult result = analyzer.analyze(event);
            eventPersistenceService.save(event, result);
            Boolean matchesExpected = null;
            analyzed++;
            if (event.hasExpectedResult()) {
                labeled++;
                if (alertService.matchesExpected(event, result)) {
                    matchedExpected++;
                    matchesExpected = true;
                } else {
                    mismatchedExpected++;
                    matchesExpected = false;
                }
            }
            results.add(ScanResult.from(event, result, matchesExpected));
            if (shouldPrintToConsole(result)) {
                alertService.printAlert(event, result);
            }
        }

        ScanSummary summary = new ScanSummary(
                events.size(),
                analyzed,
                duplicatesSkipped,
                labeled,
                matchedExpected,
                mismatchedExpected
        );

        alertService.printSummary(summary);
        reportExportService.export(results, summary);
        return summary;
    }

    private boolean isDuplicate(NewsEvent event) {
        return duplicateNewsFilter.isDuplicate(event) || alreadyStored(event);
    }

    private boolean alreadyStored(NewsEvent event) {
        if (event.sourceUrl() == null || event.sourceUrl().isBlank()) {
            return false;
        }
        return articleRepository.existsByUrlHash(EventPersistenceService.urlHash(event.sourceUrl()));
    }

    private boolean shouldPrintToConsole(AnalysisResult result) {
        EventStatus minimumStatus = consoleSettings.consoleMinStatus();
        return minimumStatus == null || result.status().isAtLeast(minimumStatus);
    }
}
