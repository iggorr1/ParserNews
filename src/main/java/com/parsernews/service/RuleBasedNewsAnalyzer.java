package com.parsernews.service;

import com.parsernews.config.AnalyzerRules;
import com.parsernews.config.AnalyzerSettings;
import com.parsernews.config.KeywordRule;
import com.parsernews.config.RulesConfigLoader;
import com.parsernews.config.StatusThresholds;
import com.parsernews.model.AnalysisResult;
import com.parsernews.model.EventStatus;
import com.parsernews.model.EventType;
import com.parsernews.model.NewsEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RuleBasedNewsAnalyzer {
    private final AnalyzerRules rules;
    private final AnalyzerSettings settings;
    private final FalsePositiveFilter falsePositiveFilter;

    @Autowired
    public RuleBasedNewsAnalyzer(
            RulesConfigLoader rulesConfigLoader,
            AnalyzerSettings settings,
            FalsePositiveFilter falsePositiveFilter
    ) {
        this(rulesConfigLoader.loadRules(), settings, falsePositiveFilter);
    }

    public RuleBasedNewsAnalyzer(AnalyzerRules rules) {
        this(rules, new AnalyzerSettings(null, null, null), new FalsePositiveFilter());
    }

    public RuleBasedNewsAnalyzer(RulesConfigLoader rulesConfigLoader) {
        this(rulesConfigLoader.loadRules());
    }

    public RuleBasedNewsAnalyzer(AnalyzerRules rules, AnalyzerSettings settings) {
        this(rules, settings, new FalsePositiveFilter());
    }

    public RuleBasedNewsAnalyzer(AnalyzerRules rules, AnalyzerSettings settings, FalsePositiveFilter falsePositiveFilter) {
        this.rules = rules;
        this.settings = settings;
        this.falsePositiveFilter = falsePositiveFilter;
    }

    public AnalysisResult analyze(NewsEvent event) {
        String text = event.fullText().toLowerCase(Locale.ROOT);
        List<String> positives = new ArrayList<>();
        List<String> negatives = new ArrayList<>();
        int score = 0;

        for (KeywordRule rule : rules.positiveRules()) {
            String keyword = normalize(rule.keyword());
            if (text.contains(keyword)) {
                positives.add(keyword);
                score += rule.score();
            }
        }

        for (KeywordRule rule : rules.negativeRules()) {
            String keyword = normalize(rule.keyword());
            if (text.contains(keyword) && shouldApplyNegativeRule(keyword, text)) {
                negatives.add(keyword);
                score += rule.score();
            }
        }

        EventType eventType = determineEventType(text, positives, negatives);
        if (eventType == EventType.DEBT_TENDER_OFFER || shouldSuppressAsFalsePositive(eventType, text)) {
            eventType = eventType == EventType.DEBT_TENDER_OFFER ? eventType : EventType.NOISE;
            score = Math.min(score, 0);
            negatives.addAll(falsePositiveFilter.reasons(text));
        }
        boolean confirmedDealCandidate = isConfirmedDealCandidate(text, positives, negatives, eventType);
        if (!confirmedDealCandidate && isBroadMnaOnly(eventType)) {
            score = Math.min(score, 0);
        }
        EventStatus status = determineStatus(score, confirmedDealCandidate);
        String reason = buildReason(eventType, status, positives, negatives);

        return new AnalysisResult(eventType, status, score, positives, negatives, reason);
    }

    private String normalize(String keyword) {
        return keyword.toLowerCase(Locale.ROOT);
    }

    private boolean shouldApplyNegativeRule(String keyword, String text) {
        if (!keyword.equals("delisting notice")) {
            return true;
        }

        boolean acquisitionContext = text.contains("after acquisition")
                || text.contains("following closing")
                || text.contains("will no longer be publicly traded")
                || text.contains("become privately held");

        return !acquisitionContext;
    }

    private EventType determineEventType(String text, List<String> positives, List<String> negatives) {
        if (falsePositiveFilter.isDebtTenderOffer(text)) {
            return EventType.DEBT_TENDER_OFFER;
        }
        if (text.contains("registered direct offering") || text.contains("public offering") || text.contains("dilution")) {
            return EventType.OFFERING_OR_DILUTION;
        }
        if (text.contains("bankruptcy") || text.contains("going concern")) {
            return EventType.BANKRUPTCY_RISK;
        }
        if (text.contains("strategic alternatives")) {
            return EventType.STRATEGIC_ALTERNATIVES;
        }
        if (text.contains("tender offer")) {
            return EventType.TENDER_OFFER;
        }
        if ((text.contains("take private") || text.contains("going private")) && containsAny(negatives, "rumor", "speculation", "non-binding proposal")) {
            return EventType.TAKE_PRIVATE_RUMOR;
        }
        if (text.contains("take private") || text.contains("going private") || text.contains("become privately held")) {
            return EventType.TAKE_PRIVATE_CONFIRMED;
        }
        if (isConfirmedDealText(text)) {
            return EventType.CONFIRMED_DEAL;
        }
        if (text.contains("definitive merger agreement") || text.contains("merger agreement") || text.contains("merger")) {
            return EventType.MERGER_CONFIRMED;
        }
        if (text.contains("to be acquired by")
                || text.contains("to acquire")
                || text.contains("acquires")
                || text.contains("acquisition")
                || text.contains("sale to")) {
            return containsAny(negatives, "rumor", "speculation", "non-binding proposal")
                    ? EventType.ACQUISITION_RUMOR
                    : EventType.ACQUISITION_CONFIRMED;
        }
        if (positives.isEmpty() && negatives.isEmpty()) {
            return EventType.UNKNOWN;
        }
        return positives.isEmpty() ? EventType.NOISE : EventType.UNKNOWN;
    }

    private boolean isConfirmedDealCandidate(
            String text,
            List<String> positives,
            List<String> negatives,
            EventType eventType
    ) {
        if (eventType == EventType.DEBT_TENDER_OFFER || falsePositiveFilter.isNonBuyoutAcquisition(text)) {
            return false;
        }
        if (containsAny(negatives, "rumor", "speculation", "non-binding proposal")) {
            return false;
        }

        boolean dealCommitment = isConfirmedDealText(text)
                || containsAny(positives,
                "definitive merger agreement",
                "definitive agreement",
                "to be acquired by",
                "going private",
                "take private",
                "shareholders will receive",
                "per share in cash");
        boolean shareholderConsideration = text.contains("per share")
                || text.contains("shareholders will receive")
                || text.contains("stockholders will receive")
                || text.contains("cash consideration")
                || text.contains("all-cash transaction")
                || text.contains("premium of")
                || text.contains("outstanding shares")
                || text.contains("common stock");
        boolean publicCompanyContext = text.contains("shareholders")
                || text.contains("stockholders")
                || text.contains("publicly traded")
                || text.contains("listed")
                || text.contains("common stock")
                || text.contains("outstanding shares")
                || text.contains("will no longer be publicly traded");

        return (dealCommitment && shareholderConsideration)
                || (dealCommitment && publicCompanyContext)
                || eventType == EventType.TAKE_PRIVATE_CONFIRMED;
    }

    private boolean isConfirmedDealText(String text) {
        return text.contains("entered into a definitive agreement")
                || text.contains("enters definitive agreement")
                || text.contains("enters into definitive agreement")
                || text.contains("definitive agreement to be acquired")
                || text.contains("definitive merger agreement")
                || text.contains("to be acquired by")
                || text.contains("will be acquired by")
                || text.contains("will acquire all outstanding shares")
                || text.contains("acquire all outstanding shares")
                || text.contains("shareholders will receive")
                || text.contains("stockholders will receive")
                || text.contains("will become privately held")
                || text.contains("will no longer be publicly traded");
    }

    private boolean isBroadMnaOnly(EventType eventType) {
        return eventType == EventType.ACQUISITION_CONFIRMED
                || eventType == EventType.MERGER_CONFIRMED
                || eventType == EventType.CONFIRMED_DEAL
                || eventType == EventType.TENDER_OFFER
                || eventType == EventType.UNKNOWN;
    }

    private boolean shouldSuppressAsFalsePositive(EventType eventType, String text) {
        if (!falsePositiveFilter.isNonBuyoutAcquisition(text)) {
            return false;
        }
        return eventType != EventType.OFFERING_OR_DILUTION
                && eventType != EventType.BANKRUPTCY_RISK
                && eventType != EventType.STRATEGIC_ALTERNATIVES;
    }

    private boolean containsAny(List<String> values, String... expectedValues) {
        for (String expectedValue : expectedValues) {
            if (values.contains(expectedValue)) {
                return true;
            }
        }
        return false;
    }

    private EventStatus determineStatus(int score, boolean confirmedDealCandidate) {
        if (!confirmedDealCandidate) {
            return EventStatus.IGNORED;
        }
        StatusThresholds thresholds = rules.statusThresholds();
        int importantThreshold = threshold(settings.importantThresholdOverride(), thresholds.important());
        int manualReviewThreshold = threshold(settings.manualReviewThresholdOverride(), thresholds.manualReview());
        int watchlistThreshold = threshold(settings.watchlistThresholdOverride(), thresholds.watchlist());

        if (score >= importantThreshold) {
            return EventStatus.IMPORTANT;
        }
        if (score >= manualReviewThreshold) {
            return EventStatus.MANUAL_REVIEW;
        }
        if (score >= watchlistThreshold) {
            return EventStatus.WATCHLIST;
        }
        return EventStatus.IGNORED;
    }

    private int threshold(Integer override, int fallback) {
        return override == null ? fallback : override;
    }

    private String buildReason(EventType eventType, EventStatus status, List<String> positives, List<String> negatives) {
        if (positives.isEmpty() && negatives.isEmpty()) {
            return "No configured acquisition or risk keywords matched.";
        }
        return "Classified as " + eventType + " with status " + status
                + " from " + positives.size() + " positive and " + negatives.size() + " negative keyword matches.";
    }
}
