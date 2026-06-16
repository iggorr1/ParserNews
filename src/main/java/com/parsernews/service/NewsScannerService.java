package com.parsernews.service;

import com.parsernews.model.AnalysisResult;
import com.parsernews.model.NewsEvent;
import com.parsernews.parser.NewsSourceParser;
import org.springframework.stereotype.Service;

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
        for (NewsEvent event : newsSourceParser.readNews()) {
            if (duplicateNewsFilter.isDuplicate(event)) {
                continue;
            }
            AnalysisResult result = analyzer.analyze(event);
            alertService.printAlert(event, result);
        }
    }
}
