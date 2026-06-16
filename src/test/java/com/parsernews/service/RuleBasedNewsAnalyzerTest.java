package com.parsernews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.config.RulesConfigLoader;
import com.parsernews.model.AnalysisResult;
import com.parsernews.model.EventStatus;
import com.parsernews.model.EventType;
import com.parsernews.model.NewsEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedNewsAnalyzerTest {
    private final RuleBasedNewsAnalyzer analyzer = new RuleBasedNewsAnalyzer(
            new RulesConfigLoader(new ObjectMapper())
    );

    @Test
    void marksConfirmedTakePrivateCashDealAsImportant() {
        AnalysisResult result = analyzer.analyze(news(
                "Company Enters Definitive Merger Agreement to Be Acquired by Private Equity Buyer",
                "Shareholders will receive $10.00 per share in cash in an all-cash transaction. " +
                        "Following closing, the company will become privately held and stock will no longer be publicly traded."
        ));

        assertThat(result.eventType()).isEqualTo(EventType.TAKE_PRIVATE_CONFIRMED);
        assertThat(result.status()).isEqualTo(EventStatus.IMPORTANT);
        assertThat(result.score()).isGreaterThanOrEqualTo(76);
        assertThat(result.matchedPositiveKeywords())
                .contains("definitive merger agreement", "per share in cash", "all-cash transaction");
        assertThat(result.matchedNegativeKeywords()).isEmpty();
    }

    @Test
    void lowersScoreForNonBindingTakePrivateRumor() {
        AnalysisResult result = analyzer.analyze(news(
                "Company Receives Non-Binding Proposal from Investment Firm",
                "The proposal is a take private rumor and remains speculation."
        ));

        assertThat(result.eventType()).isEqualTo(EventType.TAKE_PRIVATE_RUMOR);
        assertThat(result.status()).isEqualTo(EventStatus.IGNORED);
        assertThat(result.matchedPositiveKeywords()).contains("take private", "investment firm");
        assertThat(result.matchedNegativeKeywords()).contains("non-binding proposal", "rumor", "speculation");
    }

    @Test
    void treatsRegisteredDirectOfferingAsDilutionNoise() {
        AnalysisResult result = analyzer.analyze(news(
                "Company Announces Registered Direct Offering",
                "The public offering may result in dilution."
        ));

        assertThat(result.eventType()).isEqualTo(EventType.OFFERING_OR_DILUTION);
        assertThat(result.status()).isEqualTo(EventStatus.IGNORED);
        assertThat(result.score()).isLessThan(31);
        assertThat(result.matchedNegativeKeywords())
                .contains("registered direct offering", "public offering", "dilution");
    }

    @Test
    void ignoresDelistingNoticePenaltyWhenDelistingIsAfterAcquisition() {
        AnalysisResult result = analyzer.analyze(news(
                "Company Signs Definitive Merger Agreement",
                "Shareholders will receive $5.00 per share in cash. After acquisition, the company received a delisting notice and will become privately held."
        ));

        assertThat(result.eventType()).isEqualTo(EventType.TAKE_PRIVATE_CONFIRMED);
        assertThat(result.matchedPositiveKeywords()).contains("definitive merger agreement", "per share in cash");
        assertThat(result.matchedNegativeKeywords()).doesNotContain("delisting notice");
    }

    @Test
    void classifiesBankruptcyAndGoingConcernAsRisk() {
        AnalysisResult result = analyzer.analyze(news(
                "Company Warns About Going Concern",
                "The company disclosed bankruptcy risk after receiving a delisting notice."
        ));

        assertThat(result.eventType()).isEqualTo(EventType.BANKRUPTCY_RISK);
        assertThat(result.status()).isEqualTo(EventStatus.IGNORED);
        assertThat(result.matchedNegativeKeywords()).contains("bankruptcy", "going concern", "delisting notice");
    }

    private NewsEvent news(String headline, String body) {
        return new NewsEvent("TEST", "Test Company", headline, body, "Test Source", "https://example.com/test");
    }
}
