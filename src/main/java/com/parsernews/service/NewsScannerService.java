package com.parsernews.service;

import com.parsernews.model.AnalysisResult;
import com.parsernews.model.NewsEvent;
import com.parsernews.model.ScanResult;
import com.parsernews.model.ScanSummary;
import com.parsernews.parser.NewsSourceParser;
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

    public NewsScannerService(
            NewsSourceParser newsSourceParser,
            RuleBasedNewsAnalyzer analyzer,
            AlertService alertService,
            DuplicateNewsFilter duplicateNewsFilter,
            ReportExportService reportExportService
    ) {
        this.newsSourceParser = newsSourceParser;
        this.analyzer = analyzer;
        this.alertService = alertService;
        this.duplicateNewsFilter = duplicateNewsFilter;
        this.reportExportService = reportExportService;
    }

    public void scan() {
        List<NewsEvent> events = newsSourceParser.readNews();
        List<ScanResult> results = new ArrayList<>();
        int analyzed = 0;
        int duplicatesSkipped = 0;
        int labeled = 0;
        int matchedExpected = 0;
        int mismatchedExpected = 0;

        for (NewsEvent event : events) {
            if (duplicateNewsFilter.isDuplicate(event)) {
                duplicatesSkipped++;
                continue;
            }
            AnalysisResult result = analyzer.analyze(event);
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
            alertService.printAlert(event, result);
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
    }
}
