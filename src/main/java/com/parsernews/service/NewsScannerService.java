package com.parsernews.service;

import com.parsernews.model.AnalysisResult;
import com.parsernews.model.NewsEvent;
import com.parsernews.model.ScanSummary;
import com.parsernews.parser.NewsSourceParser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NewsScannerService {
    private final NewsSourceParser newsSourceParser;
    private final RuleBasedNewsAnalyzer analyzer;
    private final AlertService alertService;
    private final DuplicateNewsFilter duplicateNewsFilter;

    public NewsScannerService(
            NewsSourceParser newsSourceParser,
            RuleBasedNewsAnalyzer analyzer,
            AlertService alertService,
            DuplicateNewsFilter duplicateNewsFilter
    ) {
        this.newsSourceParser = newsSourceParser;
        this.analyzer = analyzer;
        this.alertService = alertService;
        this.duplicateNewsFilter = duplicateNewsFilter;
    }

    public void scan() {
        List<NewsEvent> events = newsSourceParser.readNews();
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
            analyzed++;
            if (event.hasExpectedResult()) {
                labeled++;
                if (alertService.matchesExpected(event, result)) {
                    matchedExpected++;
                } else {
                    mismatchedExpected++;
                }
            }
            alertService.printAlert(event, result);
        }

        alertService.printSummary(new ScanSummary(
                events.size(),
                analyzed,
                duplicatesSkipped,
                labeled,
                matchedExpected,
                mismatchedExpected
        ));
    }
}
