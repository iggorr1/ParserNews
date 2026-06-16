package com.parsernews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.config.AnalyzerSettings;
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
    void marksConfirmedTakePrivateCashDealAsHighPrioritySignal() {
        AnalysisResult result = analyzer.analyze(news(
                "Company Enters Definitive Merger Agreement to Be Acquired by Private Equity Buyer",
                "Shareholders will receive $10.00 per share in cash in an all-cash transaction. " +
                        "Following closing, the company will become privately held and stock will no longer be publicly traded."
        ));

        assertThat(result.eventType()).isEqualTo(EventType.TAKE_PRIVATE_CONFIRMED);
        assertThat(result.status()).isEqualTo(EventStatus.HIGH_PRIORITY_SIGNAL);
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

    @Test
    void ignoresWeakAcquisitionHeadlineWithoutShareholderDealTerms() {
        AnalysisResult result = analyzer.analyze(news(
                "Buyer Corp to Acquire Target Corp",
                "The companies announced an acquisition."
        ));

        assertThat(result.eventType()).isEqualTo(EventType.ACQUISITION_CONFIRMED);
        assertThat(result.status()).isEqualTo(EventStatus.IGNORED);
        assertThat(result.score()).isEqualTo(0);
        assertThat(result.matchedPositiveKeywords()).contains("to acquire", "acquisition");
    }

    @Test
    void liveDiscoveryDoesNotPromoteWeakAcquisitionWithoutDealTerms() {
        RuleBasedNewsAnalyzer liveAnalyzer = new RuleBasedNewsAnalyzer(
                new RulesConfigLoader(new ObjectMapper()).loadRules(),
                new AnalyzerSettings(1, null, null)
        );

        AnalysisResult result = liveAnalyzer.analyze(news(
                "Buyer Corp to Acquire Target Corp",
                "The companies announced an acquisition."
        ));

        assertThat(result.status()).isEqualTo(EventStatus.IGNORED);
    }

    @Test
    void marksConfirmedShareholderDealAsWatchlistInLiveDiscovery() {
        RuleBasedNewsAnalyzer liveAnalyzer = new RuleBasedNewsAnalyzer(
                new RulesConfigLoader(new ObjectMapper()).loadRules(),
                new AnalyzerSettings(1, null, null)
        );

        AnalysisResult result = liveAnalyzer.analyze(news(
                "Target Corp Enters Definitive Agreement to Be Acquired by Buyer Corp",
                "Target shareholders will receive $4.00 per share in cash, representing a premium of 35%."
        ));

        assertThat(result.eventType()).isEqualTo(EventType.CONFIRMED_DEAL);
        assertThat(result.status()).isEqualTo(EventStatus.HIGH_PRIORITY_SIGNAL);
        assertThat(result.score()).isGreaterThan(0);
        assertThat(result.offerPrice()).isEqualTo("$4.00");
        assertThat(result.cashOrStock()).isEqualTo("CASH");
        assertThat(result.acquirer()).isEqualTo("Buyer Corp");
        assertThat(result.premiumPercent()).isEqualTo("35%");
        assertThat(result.matchedPositiveKeywords())
                .contains("definitive agreement", "to be acquired by", "per share in cash", "premium of");
    }

    @Test
    void extractsDealTermsFromConfirmedCashTakeover() {
        AnalysisResult result = analyzer.analyze(news(
                "Target Corp to be Acquired by Northstar Capital",
                "Target Corp (NASDAQ: TGTX) entered into a definitive merger agreement. " +
                        "Shareholders will receive $3.15 per share in cash, representing a premium of 44.5%."
        ));

        assertThat(result.status()).isEqualTo(EventStatus.HIGH_PRIORITY_SIGNAL);
        assertThat(result.targetTicker()).isEqualTo("TGTX");
        assertThat(result.offerPrice()).isEqualTo("$3.15");
        assertThat(result.cashOrStock()).isEqualTo("CASH");
        assertThat(result.acquirer()).isEqualTo("Northstar Capital");
        assertThat(result.premiumPercent()).isEqualTo("44.5%");
    }

    @Test
    void filtersDebtTenderOfferForSeniorNotes() {
        RuleBasedNewsAnalyzer liveAnalyzer = new RuleBasedNewsAnalyzer(
                new RulesConfigLoader(new ObjectMapper()).loadRules(),
                new AnalyzerSettings(1, null, null)
        );

        AnalysisResult result = liveAnalyzer.analyze(news(
                "Fiserv Announces Launch of Tender Offers for Senior Notes due 2027",
                "The company launched tender offers for debt securities and bonds."
        ));

        assertThat(result.eventType()).isEqualTo(EventType.DEBT_TENDER_OFFER);
        assertThat(result.status()).isEqualTo(EventStatus.IGNORED);
        assertThat(result.score()).isLessThanOrEqualTo(0);
        assertThat(result.matchedNegativeKeywords()).contains("senior notes");
    }

    @Test
    void ignoresAssetAcquisitionEvenWhenAcquisitionKeywordMatches() {
        RuleBasedNewsAnalyzer liveAnalyzer = new RuleBasedNewsAnalyzer(
                new RulesConfigLoader(new ObjectMapper()).loadRules(),
                new AnalyzerSettings(1, null, null)
        );

        AnalysisResult result = liveAnalyzer.analyze(news(
                "Buyer Completes Acquisition of Manufacturing Assets",
                "The asset acquisition does not involve buying public company shares."
        ));

        assertThat(result.eventType()).isEqualTo(EventType.NOISE);
        assertThat(result.status()).isEqualTo(EventStatus.IGNORED);
        assertThat(result.score()).isLessThanOrEqualTo(0);
        assertThat(result.matchedNegativeKeywords()).contains("asset acquisition");
    }

    @Test
    void ignoresFacilitySaleEvenWithDefinitiveAgreementLanguage() {
        RuleBasedNewsAnalyzer liveAnalyzer = new RuleBasedNewsAnalyzer(
                new RulesConfigLoader(new ObjectMapper()).loadRules(),
                new AnalyzerSettings(1, null, null)
        );

        AnalysisResult result = liveAnalyzer.analyze(news(
                "Company Announces Strategic Exit With Sale of Facility",
                "The company entered into a definitive agreement and will receive cash consideration for the sale of facility."
        ));

        assertThat(result.eventType()).isEqualTo(EventType.NOISE);
        assertThat(result.status()).isEqualTo(EventStatus.IGNORED);
        assertThat(result.matchedNegativeKeywords()).contains("sale of facility", "strategic exit");
    }

    @Test
    void ignoresCompletedTakePrivateBecauseItIsNotAnEarlySignal() {
        RuleBasedNewsAnalyzer liveAnalyzer = new RuleBasedNewsAnalyzer(
                new RulesConfigLoader(new ObjectMapper()).loadRules(),
                new AnalyzerSettings(1, null, null)
        );

        AnalysisResult result = liveAnalyzer.analyze(news(
                "Buyer Announces Completion of Take-Private Transaction",
                "Shareholders received $10.00 per share in cash."
        ));

        assertThat(result.eventType()).isEqualTo(EventType.NOISE);
        assertThat(result.status()).isEqualTo(EventStatus.IGNORED);
        assertThat(result.matchedNegativeKeywords()).contains("completion of");
    }

    private NewsEvent news(String headline, String body) {
        return new NewsEvent("TEST", "Test Company", headline, body, "Test Source", "https://example.com/test");
    }
}
