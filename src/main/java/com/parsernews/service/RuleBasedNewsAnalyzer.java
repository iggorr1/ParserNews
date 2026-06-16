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

    @Autowired
    public RuleBasedNewsAnalyzer(RulesConfigLoader rulesConfigLoader, AnalyzerSettings settings) {
        this(rulesConfigLoader.loadRules(), settings);
    }

    public RuleBasedNewsAnalyzer(AnalyzerRules rules) {
        this(rules, new AnalyzerSettings(null, null, null));
    }

    public RuleBasedNewsAnalyzer(RulesConfigLoader rulesConfigLoader) {
        this(rulesConfigLoader.loadRules());
    }

    public RuleBasedNewsAnalyzer(AnalyzerRules rules, AnalyzerSettings settings) {
        this.rules = rules;
        this.settings = settings;
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
        EventStatus status = determineStatus(score);
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

    private boolean containsAny(List<String> values, String... expectedValues) {
        for (String expectedValue : expectedValues) {
            if (values.contains(expectedValue)) {
                return true;
            }
        }
        return false;
    }

    private EventStatus determineStatus(int score) {
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
