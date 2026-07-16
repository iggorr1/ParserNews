package com.parsernews.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RulesConfigLoaderTest {
    private final RulesConfigLoader loader = new RulesConfigLoader(new ObjectMapper());

    @Test
    void readsAnalyzerRulesFromJsonResource() {
        AnalyzerRules rules = loader.loadRules();

        assertThat(rules.positiveRules())
                .extracting(KeywordRule::keyword)
                .contains(
                        "definitive merger agreement",
                        "to be acquired by",
                        "to acquire",
                        "acquisition"
                );
        assertThat(rules.negativeRules())
                .extracting(KeywordRule::keyword)
                .contains("public offering", "rumor", "class action", "shareholder alert");
        assertThat(rules.statusThresholds().watchlist()).isEqualTo(31);
        assertThat(rules.statusThresholds().manualReview()).isEqualTo(56);
        assertThat(rules.statusThresholds().important()).isEqualTo(76);
    }
}
