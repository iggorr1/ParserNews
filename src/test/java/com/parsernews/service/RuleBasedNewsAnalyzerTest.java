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

    // Real headlines that the live rules scored 0 and IGNORED. Aggregator headlines carry no
    // article body, so the confirmation gate can only ever see the headline itself — and it
    // missed these because the price is written "per-share" (hyphenated) rather than "per share",
    // and "all-cash merger" rather than "all-cash transaction".
    @Test
    void detectsCashBuyoutWhenHeadlineHyphenatesPerShare() {
        AnalysisResult result = analyzer.analyze(news(
                "LiveRamp to Be Acquired by Publicis in $38.50-Per-Share All-Cash Merger",
                ""
        ));

        assertThat(result.status()).isNotEqualTo(EventStatus.IGNORED);
        assertThat(result.score()).isGreaterThan(0);
    }

    @Test
    void detectsCashPlusCvrBuyoutHeadline() {
        AnalysisResult result = analyzer.analyze(news(
                "AtaiBeckley to Be Acquired by Eli Lilly for $6.75 Cash Plus Up to $2.50 CVR",
                ""
        ));

        assertThat(result.status()).isNotEqualTo(EventStatus.IGNORED);
        assertThat(result.score()).isGreaterThan(0);
    }

    // Real IGNORED article: a merger of two public companies announced as "file to combine".
    // The vocabulary did not know that phrasing, and the release never says "definitive
    // agreement", so nothing marked it as a committed deal and its score was forced to 0.
    // The body mirrors the stored article: it mentions shareholders but states no cash terms.
    @Test
    void detectsMergerAnnouncedAsFileToCombine() {
        AnalysisResult result = analyzer.analyze(news(
                "NextEra Energy and Dominion Energy file to combine, building a stronger company "
                        + "to meet growing power demand across four of America's fastest-growing states",
                "The combined company will serve customers across four states. "
                        + "The merger is subject to approval by shareholders and regulators."
        ));

        assertThat(result.status()).isNotEqualTo(EventStatus.IGNORED);
    }

    // Law-firm alerts quote the real deal terms verbatim ("$17 per share plus CVR"), so they score
    // like a genuine announcement. They previously stayed below the alert threshold only by
    // accident; keep them out explicitly.
    @Test
    void suppressesLawFirmClassActionAlertThatQuotesDealTerms() {
        AnalysisResult result = analyzer.analyze(news(
                "SHAREHOLDER ALERT: The M&A Class Action Firm Continues to Investigate the Merger "
                        + "-- TBPH: Theravance Biopharma to Be Acquired for $17 Per Share Plus CVR",
                ""
        ));

        assertThat(result.status()).isNotEqualTo(EventStatus.HIGH_PRIORITY_SIGNAL);
        assertThat(result.status()).isNotEqualTo(EventStatus.IMPORTANT);
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
        assertThat(result.matchedNegativeKeywords()).contains("completion of take-private transaction");
    }

    @Test
    void doesNotIgnoreRealAcquisitionOfferOnlyBecauseTextMentionsCompletionOfTransaction() {
        AnalysisResult result = analyzer.analyze(news(
                "Diana Shipping Inc. Raises Offer to Acquire Genco Shipping & Trading",
                "The company entered into a definitive agreement and shareholders will receive $17.25 per share in cash. " +
                        "The parties expect completion of the transaction after customary closing conditions."
        ));

        assertThat(result.status()).isNotEqualTo(EventStatus.IGNORED);
        assertThat(result.score()).isGreaterThan(0);
        assertThat(result.matchedPositiveKeywords())
                .contains("definitive agreement", "to acquire", "shareholders will receive", "per share in cash");
        assertThat(result.matchedNegativeKeywords()).doesNotContain("completion of");
    }

    private NewsEvent news(String headline, String body) {
        return new NewsEvent("TEST", "Test Company", headline, body, "Test Source", "https://example.com/test");
    }
}
