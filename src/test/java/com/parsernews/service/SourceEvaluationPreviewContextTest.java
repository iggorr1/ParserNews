package com.parsernews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.config.RssSettings;
import com.parsernews.config.RulesConfigLoader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceEvaluationPreviewContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SourceEvaluationPreviewService.class)
            .withBean(RuleBasedNewsAnalyzer.class, () -> new RuleBasedNewsAnalyzer(new RulesConfigLoader(new ObjectMapper())))
            .withBean(CandidateScoringService.class, CandidateScoringService::new)
            .withBean(CandidateReviewInsightService.class, CandidateReviewInsightService::new)
            .withBean(DealTermsExtractionService.class, DealTermsExtractionService::new)
            .withBean(DealRelevanceService.class, DealRelevanceService::new)
            .withBean(DealStageDetectionService.class, DealStageDetectionService::new)
            .withBean(AlertEligibilityService.class, AlertEligibilityService::new)
            .withBean(FalsePositiveFilter.class, FalsePositiveFilter::new)
            .withBean(RssSettings.class, () -> new RssSettings(List.of("https://example.com/rss.xml"), 20, 20, false, List.of()));

    @Test
    void sourceEvaluationPreviewServiceLoadsFromSpringContext() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(SourceEvaluationPreviewService.class));
    }
}
