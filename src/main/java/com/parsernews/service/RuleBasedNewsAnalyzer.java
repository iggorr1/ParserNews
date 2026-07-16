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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuleBasedNewsAnalyzer {
    private static final Pattern EXCHANGE_TICKER = Pattern.compile(
            "(?i)(?:NYSE\\s+American|NYSEAMERICAN|NASDAQ|NYSE|AMEX|OTCQB|OTCQX|OTC|OTCMKTS|TSX|TSXV)\\s*:\\s*([A-Z][A-Z0-9.-]{0,9})"
    );
    private static final Pattern OFFER_PRICE = Pattern.compile(
            "(?i)(?:\\$|US\\$)\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:per share|per common share)"
    );
    private static final Pattern PREMIUM_PERCENT = Pattern.compile(
            "(?i)premium of\\s+([0-9]+(?:\\.[0-9]+)?)\\s*%"
    );
    private static final Pattern ACQUIRER_BY = Pattern.compile(
            "(?i)\\b(?:to be|will be) acquired by\\s+([^,.]+?)(?=\\s+(?:for|in|under|at|through|shareholders|stockholders|will receive)\\b|[,.]|$)"
    );
    private static final Pattern ACQUIRER_AGREEMENT_WITH = Pattern.compile(
            "(?i)\\b(?:definitive agreement|merger agreement)\\s+with\\s+([^,.]+?)(?=\\s+(?:for|in|under|at|through|shareholders|stockholders|will receive)\\b|[,.]|$)"
    );

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
        DealTerms dealTerms = extractDealTerms(event);
        EventStatus status = determineStatus(score, confirmedDealCandidate, dealTerms);
        String reason = buildReason(eventType, status, positives, negatives, dealTerms);

        return new AnalysisResult(
                eventType,
                status,
                score,
                dealTerms.targetTicker(),
                dealTerms.offerPrice(),
                dealTerms.cashOrStock(),
                dealTerms.acquirer(),
                dealTerms.premiumPercent(),
                positives,
                negatives,
                reason
        );
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
        // SEC form names in EDGAR filings — map to deal types directly
        if (text.contains("sc to-t") || text.contains("sc to-t/a")) {
            return EventType.TENDER_OFFER;
        }
        if (text.contains("defm14a") || text.contains("prem14a")) {
            return EventType.MERGER_CONFIRMED;
        }
        if (text.contains("sc to-i") || text.contains("sc to-i/a")) {
            return EventType.TENDER_OFFER;
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
                "per share in cash",
                // A regulatory filing to combine, and a merger of equals, are announced deals in
                // their own right — they commit to the deal without ever saying "definitive
                // agreement" (NextEra/Dominion "file to combine" said neither, and was ignored).
                "file to combine",
                "merger of equals");
        // Aggregator headlines are terse and carry no article body, so this gate only ever sees the
        // headline. It must therefore accept the compact spellings they actually use: a hyphenated
        // "$38.50-per-share", "all-cash merger" (not just "all-cash transaction"), and a CVR — all
        // of which state shareholder consideration just as plainly as the long forms.
        boolean shareholderConsideration = text.contains("per share")
                || text.contains("per-share")
                || text.contains("shareholders will receive")
                || text.contains("stockholders will receive")
                || text.contains("cash consideration")
                || text.contains("all-cash")
                || text.contains("contingent value right")
                || text.contains("cvr")
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

        // SEC EDGAR filings are authoritative — treat as confirmed even without full text
        boolean secFiling = text.contains("sc to-t") || text.contains("defm14a") || text.contains("prem14a");

        return (dealCommitment && shareholderConsideration)
                || (dealCommitment && publicCompanyContext)
                || eventType == EventType.TAKE_PRIVATE_CONFIRMED
                || (secFiling && (eventType == EventType.TENDER_OFFER || eventType == EventType.MERGER_CONFIRMED));
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

    private EventStatus determineStatus(int score, boolean confirmedDealCandidate, DealTerms dealTerms) {
        if (!confirmedDealCandidate) {
            return EventStatus.IGNORED;
        }
        StatusThresholds thresholds = rules.statusThresholds();
        int importantThreshold = threshold(settings.importantThresholdOverride(), thresholds.important());
        int manualReviewThreshold = threshold(settings.manualReviewThresholdOverride(), thresholds.manualReview());
        int watchlistThreshold = threshold(settings.watchlistThresholdOverride(), thresholds.watchlist());

        if (score >= manualReviewThreshold && isHighPrioritySignal(dealTerms)) {
            return EventStatus.HIGH_PRIORITY_SIGNAL;
        }
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

    private boolean isHighPrioritySignal(DealTerms dealTerms) {
        boolean hasOfferPrice = dealTerms.offerPrice() != null;
        boolean hasShareholderPaymentType = "CASH".equals(dealTerms.cashOrStock())
                || "CASH_AND_STOCK".equals(dealTerms.cashOrStock());
        boolean hasPremium = dealTerms.premiumPercent() != null;
        return hasOfferPrice && (hasShareholderPaymentType || hasPremium);
    }

    private int threshold(Integer override, int fallback) {
        return override == null ? fallback : override;
    }

    private DealTerms extractDealTerms(NewsEvent event) {
        String text = event.fullText();
        String lower = text.toLowerCase(Locale.ROOT);
        return new DealTerms(
                firstTicker(EXCHANGE_TICKER.matcher(text)),
                firstMoney(OFFER_PRICE.matcher(text)),
                cashOrStock(lower),
                extractAcquirer(event),
                firstPercent(PREMIUM_PERCENT.matcher(text))
        );
    }

    private String extractAcquirer(NewsEvent event) {
        String fromHeadline = firstGroup(
                ACQUIRER_BY.matcher(event.headline()),
                ACQUIRER_AGREEMENT_WITH.matcher(event.headline())
        );
        if (fromHeadline != null) {
            return cleanPartyName(fromHeadline);
        }
        String fromFullText = firstGroup(
                ACQUIRER_BY.matcher(event.fullText()),
                ACQUIRER_AGREEMENT_WITH.matcher(event.fullText())
        );
        return cleanPartyName(fromFullText);
    }

    private String firstTicker(Matcher matcher) {
        if (matcher.find()) {
            return matcher.group(1).replace(".", "-").trim().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private String firstGroup(Matcher... matchers) {
        for (Matcher matcher : matchers) {
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private String firstMoney(Matcher matcher) {
        if (matcher.find()) {
            return "$" + matcher.group(1);
        }
        return null;
    }

    private String firstPercent(Matcher matcher) {
        if (matcher.find()) {
            return matcher.group(1) + "%";
        }
        return null;
    }

    private String cleanPartyName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim()
                .replaceAll("(?i)\\s+(inc|corp|corporation|llc|ltd)\\s*$", " $1")
                .replaceAll("\\s+", " ");
        return cleaned.length() > 120 ? cleaned.substring(0, 120).trim() : cleaned;
    }

    private String cashOrStock(String text) {
        boolean cash = text.contains("all-cash")
                || text.contains("per share in cash")
                || text.contains("cash consideration")
                || text.contains("in cash");
        boolean stock = text.contains("stock consideration")
                || text.contains("stock-for-stock")
                || text.contains("shares of")
                || text.contains("common stock of");
        if (cash && stock) {
            return "CASH_AND_STOCK";
        }
        if (cash) {
            return "CASH";
        }
        if (stock) {
            return "STOCK";
        }
        return null;
    }

    private String buildReason(
            EventType eventType,
            EventStatus status,
            List<String> positives,
            List<String> negatives,
            DealTerms dealTerms
    ) {
        if (positives.isEmpty() && negatives.isEmpty()) {
            return "No configured acquisition or risk keywords matched.";
        }
        String details = dealTerms.summary();
        String suffix = details.isBlank() ? "" : " Extracted deal terms: " + details + ".";
        return "Classified as " + eventType + " with status " + status
                + " from " + positives.size() + " positive and " + negatives.size() + " negative keyword matches."
                + suffix;
    }

    private record DealTerms(
            String targetTicker,
            String offerPrice,
            String cashOrStock,
            String acquirer,
            String premiumPercent
    ) {
        String summary() {
            List<String> values = new ArrayList<>();
            if (offerPrice != null) {
                values.add("offer price " + offerPrice);
            }
            if (cashOrStock != null) {
                values.add("payment " + cashOrStock);
            }
            if (acquirer != null) {
                values.add("buyer " + acquirer);
            }
            if (premiumPercent != null) {
                values.add("premium " + premiumPercent);
            }
            return String.join(", ", values);
        }
    }
}
