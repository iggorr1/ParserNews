package com.parsernews.config;

import java.util.List;

public record AnalyzerRules(
        List<KeywordRule> positiveRules,
        List<KeywordRule> negativeRules,
        StatusThresholds statusThresholds
) {
}
