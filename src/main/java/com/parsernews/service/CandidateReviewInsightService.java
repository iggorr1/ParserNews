package com.parsernews.service;

import com.parsernews.model.ReviewVerdict;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.ReviewStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CandidateReviewInsightService {
    private static final List<SignalRule> POSITIVE_SIGNAL_RULES = List.of(
            new SignalRule("definitive agreement", "definitive agreement"),
            new SignalRule("merger agreement", "merger agreement"),
            new SignalRule("to be acquired by", "to be acquired by"),
            new SignalRule("acquired by", "acquired by"),
            new SignalRule("take private", "take private"),
            new SignalRule("going private", "going private"),
            new SignalRule("per share in cash", "per share in cash"),
            new SignalRule("shareholders will receive", "shareholders will receive"),
            new SignalRule("stockholders will receive", "shareholders will receive"),
            new SignalRule("all-cash transaction", "all-cash transaction"),
            new SignalRule("tender offer", "tender offer"),
            new SignalRule("enterprise value", "enterprise value")
    );

    private static final List<SignalRule> LAW_FIRM_RULES = List.of(
            new SignalRule("shareholder alert", "shareholder alert"),
            new SignalRule("hareholder alert", "shareholder alert"),
            new SignalRule("stockholder alert", "shareholder alert"),
            new SignalRule("law firm", "law firm investigation"),
            new SignalRule("investigation", "law firm investigation"),
            new SignalRule("investigates", "law firm investigation"),
            new SignalRule("m&a class action", "class action"),
            new SignalRule("class action", "class action")
    );

    private static final List<SignalRule> FINANCING_RULES = List.of(
            new SignalRule("public offering", "public offering"),
            new SignalRule("registered direct offering", "registered direct offering"),
            new SignalRule("private placement", "private placement"),
            new SignalRule("non-brokered private placement", "private placement"),
            new SignalRule("safe investment", "financing/strategic investment"),
            new SignalRule("strategic investment", "financing/strategic investment"),
            new SignalRule("senior notes", "debt tender offer"),
            new SignalRule("tender offer for notes", "debt tender offer"),
            new SignalRule("dividend", "ordinary corporate update"),
            new SignalRule("earnings release", "ordinary corporate update"),
            new SignalRule("investor conference call", "ordinary corporate update"),
            new SignalRule("debt tender offer", "debt tender offer")
    );

    private static final List<SignalRule> NOISE_RULES = List.of(
            new SignalRule("asset acquisition", "asset acquisition"),
            new SignalRule("acquisition of assets", "asset acquisition"),
            new SignalRule("acquisition of a royalty", "asset acquisition"),
            new SignalRule("royalty on", "asset acquisition"),
            new SignalRule("acquisition of a project", "asset acquisition"),
            new SignalRule("treasury asset", "asset acquisition"),
            new SignalRule("position in", "asset acquisition"),
            new SignalRule("stake in", "asset acquisition"),
            new SignalRule("loan portfolio", "asset acquisition"),
            new SignalRule("book of business", "asset acquisition"),
            new SignalRule("solar assets", "asset acquisition"),
            new SignalRule("operating assets", "asset acquisition"),
            new SignalRule("sale of assets", "sale of assets"),
            new SignalRule("form 8.3", "position/dealing disclosure"),
            new SignalRule("form 8.5", "position/dealing disclosure"),
            new SignalRule("dealing disclosure", "position/dealing disclosure"),
            new SignalRule("opening position disclosure", "position/dealing disclosure"),
            new SignalRule("joint venture", "joint venture"),
            new SignalRule("reverse stock split", "reverse stock split")
    );

    public ReviewInsight insight(NewsArticleEntity article, DetectedEventEntity event) {
        String text = combinedText(article, event);
        List<String> positiveSignals = uniqueMatches(text, POSITIVE_SIGNAL_RULES);
        List<String> riskFlags = new ArrayList<>();
        riskFlags.addAll(uniqueMatches(text, LAW_FIRM_RULES));
        riskFlags.addAll(uniqueMatches(text, FINANCING_RULES));
        riskFlags.addAll(uniqueMatches(text, NOISE_RULES));
        if (NewsTextPatterns.isRoundupAggregator(article.getHeadline(), article.getArticleText())) {
            addIfMissing(riskFlags, NewsTextPatterns.ROUNDUP_AGGREGATOR_WARNING);
        }

        CandidateStrength strength = event == null ? CandidateStrength.NONE : event.getCandidateStrength();
        int score = event == null ? 0 : event.getCandidateScore();
        boolean oldIgnoredHigh = event != null
                && event.getReviewStatus() == ReviewStatus.IGNORED
                && strength == CandidateStrength.HIGH;
        boolean tickerUnknown = article.getTicker() == null || article.getTicker().isBlank()
                || "UNKNOWN".equalsIgnoreCase(article.getTicker());
        boolean hasDealTerms = containsAny(positiveSignals,
                "per share in cash",
                "shareholders will receive",
                "all-cash transaction",
                "tender offer",
                "enterprise value");

        if (tickerUnknown && strength != CandidateStrength.NONE) {
            addIfMissing(riskFlags, "ticker unknown");
        }
        if (oldIgnoredHigh) {
            addIfMissing(riskFlags, "old reviewStatus IGNORED but candidateStrength HIGH");
        }

        ReviewVerdict verdict;
        if (containsAny(riskFlags, NewsTextPatterns.ROUNDUP_AGGREGATOR_WARNING)) {
            verdict = ReviewVerdict.LIKELY_NOISE;
        } else if (containsAny(riskFlags, "shareholder alert", "law firm investigation", "class action")) {
            verdict = ReviewVerdict.LAW_FIRM_ALERT;
        } else if (containsAny(riskFlags, "public offering", "registered direct offering", "private placement",
                "debt tender offer", "financing/strategic investment", "ordinary corporate update")) {
            verdict = ReviewVerdict.FINANCING_OR_OFFERING;
        } else if (strength == CandidateStrength.HIGH && !positiveSignals.isEmpty() && riskFlags.isEmpty()) {
            verdict = ReviewVerdict.LIKELY_DEAL;
        } else if ((strength == CandidateStrength.HIGH || strength == CandidateStrength.MEDIUM)
                && (!riskFlags.isEmpty() || !hasDealTerms)) {
            verdict = ReviewVerdict.NEEDS_REVIEW;
        } else if ((strength == CandidateStrength.LOW || strength == CandidateStrength.NONE)
                && (!riskFlags.isEmpty() || score <= 0)) {
            verdict = ReviewVerdict.LIKELY_NOISE;
        } else {
            verdict = ReviewVerdict.UNKNOWN;
        }

        return new ReviewInsight(
                verdict,
                summary(verdict, positiveSignals, riskFlags),
                riskFlags,
                positiveSignals,
                suggestedAction(verdict)
        );
    }

    private String combinedText(NewsArticleEntity article, DetectedEventEntity event) {
        List<String> values = new ArrayList<>();
        values.add(article.getHeadline());
        values.add(article.getArticleText());
        values.add(article.getSource().getName());
        values.add(article.getUrl());
        if (event != null) {
            values.add(event.getCandidateReason());
            values.add(event.getMatchedPositiveKeywords());
            values.add(event.getMatchedNegativeKeywords());
            values.add(event.getFalsePositiveReasons());
            values.add(event.getExplanation());
        }
        return String.join(" ", values.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList()).toLowerCase(Locale.ROOT);
    }

    private List<String> uniqueMatches(String text, List<SignalRule> rules) {
        List<String> matches = new ArrayList<>();
        for (SignalRule rule : rules) {
            if (text.contains(rule.keyword())) {
                addIfMissing(matches, rule.label());
            }
        }
        return matches;
    }

    private boolean containsAny(List<String> values, String... expected) {
        for (String value : expected) {
            if (values.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private void addIfMissing(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private String summary(ReviewVerdict verdict, List<String> positives, List<String> risks) {
        return switch (verdict) {
            case LIKELY_DEAL -> "Strong deal language found with no major review risk flags.";
            case NEEDS_REVIEW -> "Candidate has deal signals, but review risk flags or missing deal terms need a human check.";
            case LAW_FIRM_ALERT -> "Looks like a law firm/shareholder alert rather than primary deal news.";
            case FINANCING_OR_OFFERING -> "Looks related to financing, offering, placement, or debt tender activity.";
            case LIKELY_NOISE -> risks.isEmpty()
                    ? "Weak or no candidate signal."
                    : "Risk flags dominate the candidate signal.";
            case UNKNOWN -> positives.isEmpty()
                    ? "No clear deal or noise pattern was found."
                    : "Some positive signals were found, but the overall pattern is not decisive.";
        };
    }

    private String suggestedAction(ReviewVerdict verdict) {
        return switch (verdict) {
            case LIKELY_DEAL -> "Review source article and consider marking useful if it is a real public-company deal.";
            case NEEDS_REVIEW -> "Open source and verify ticker, deal terms, and whether the alert is primary news.";
            case LAW_FIRM_ALERT -> "Usually ignore unless it links to the original transaction announcement.";
            case FINANCING_OR_OFFERING -> "Usually ignore for takeover research.";
            case LIKELY_NOISE -> "Ignore unless manual context proves this is relevant.";
            case UNKNOWN -> "Review only if the headline looks relevant.";
        };
    }

    public record ReviewInsight(
            ReviewVerdict reviewVerdict,
            String reviewSummary,
            List<String> reviewRiskFlags,
            List<String> reviewPositiveSignals,
            String suggestedAction
    ) {
    }

    private record SignalRule(String keyword, String label) {
    }
}
