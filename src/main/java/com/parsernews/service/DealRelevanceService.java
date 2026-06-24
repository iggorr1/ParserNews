package com.parsernews.service;

import com.parsernews.model.DealRelevance;
import com.parsernews.model.PaymentType;
import com.parsernews.model.ReviewVerdict;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.CompanyMatchConfidence;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.NewsArticleEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class DealRelevanceService {
    private static final Pattern PUBLIC_TICKER = Pattern.compile(
            "(?i)(?:NASDAQ|NYSE|NYSEAMERICAN|NYSE\\s+American|AMEX|OTC|OTCQB|OTCQX|OTCMKTS|TSX|TSXV)\\s*:\\s*[A-Z][A-Z0-9.-]{0,9}"
    );

    public RelevanceInsight assess(
            NewsArticleEntity article,
            DetectedEventEntity event,
            CandidateReviewInsightService.ReviewInsight reviewInsight,
            DealTermsExtractionService.DealTerms dealTerms
    ) {
        String text = combinedText(article, event, reviewInsight, dealTerms);
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> warnings = new ArrayList<>();
        List<String> positives = new ArrayList<>();

        boolean lawFirm = reviewInsight != null
                && reviewInsight.reviewVerdict() == ReviewVerdict.LAW_FIRM_ALERT
                || containsAny(lower, "shareholder alert", "stockholder alert", "law firm", "class action");
        if (NewsTextPatterns.isRoundupAggregator(article.getHeadline(), article.getArticleText())) {
            warnings.add(NewsTextPatterns.ROUNDUP_AGGREGATOR_WARNING);
            return new RelevanceInsight(
                    DealRelevance.NOT_TRADABLE,
                    Tradability.NOT_TRADABLE,
                    "Roundup/aggregator article is not a primary tradable deal source.",
                    warnings,
                    positives
            );
        }
        if (lawFirm) {
            warnings.add("law firm/shareholder alert");
            return new RelevanceInsight(
                    DealRelevance.LAW_FIRM_OR_SHAREHOLDER_ALERT,
                    Tradability.NOT_TRADABLE,
                    "Looks like a law firm/shareholder alert, not primary tradable deal news.",
                    warnings,
                    positives
            );
        }
        if (isFinancingOrDebtNoise(lower)) {
            warnings.add("financing/debt/offering event");
            return new RelevanceInsight(
                    DealRelevance.NOT_TRADABLE,
                    Tradability.NOT_TRADABLE,
                    "Looks like financing, debt, offering, redemption, or extension-vote news, not an M&A target signal.",
                    warnings,
                    positives
            );
        }

        boolean publicBuyer = hasPublicBuyerSignal(event, lower);
        boolean privateTargetSignal = hasPrivateTargetSignal(dealTerms, lower);
        boolean publicTarget = hasPublicTargetSignal(article, event, lower, privateTargetSignal, publicBuyer);
        boolean cash = dealTerms.paymentType() == PaymentType.CASH || dealTerms.paymentType() == PaymentType.CASH_AND_STOCK;
        boolean stock = dealTerms.paymentType() == PaymentType.STOCK || dealTerms.paymentType() == PaymentType.CASH_AND_STOCK;
        boolean perShare = lower.contains("per share") || lower.contains("a share");
        boolean takePrivate = containsAny(lower, "take private", "going private", "privately held", "private equity");
        boolean reverseTakeover = containsReverseTakeover(lower);
        boolean publicPublicMerger = publicTarget && (publicBuyer
                || containsAny(lower, "public-public", "combined company", "surviving entity", "stock-for-stock"));
        boolean privateCompanySignal = privateTargetSignal
                || containsAny(lower, "portfolio company", "privately held", "private company",
                "subsidiary", "business unit", "terms were not disclosed", "terms undisclosed",
                "acquired from");

        if (!publicTarget) {
            warnings.add("target ticker missing");
            warnings.add("no public target signal");
        } else {
            positives.add("public target signal");
        }
        if (!publicBuyer && publicPublicMerger) {
            warnings.add("buyer ticker missing");
        } else if (publicBuyer) {
            positives.add("public buyer signal");
        }
        if (dealTerms.offerPrice() == null) {
            warnings.add("offer price missing");
        }
        if (dealTerms.paymentType() == PaymentType.UNKNOWN) {
            warnings.add("payment type unknown");
        }
        if (stock && !cash) {
            warnings.add("all-stock deal");
        }
        if (!cash) {
            warnings.add("no cash offer");
        }
        if (privateCompanySignal) {
            warnings.add("private-company acquisition");
        }
        if (privateTargetSignal) {
            warnings.add("private company target");
            warnings.add("not directly tradable via target shares");
        }
        if (event != null && event.isBuyerPublicCompany() && !event.isTargetPublicCompany()) {
            warnings.add("buyer resolved but target not resolved");
            warnings.add("do not infer public target from public buyer");
        }
        if (containsAny(lower, "terms were not disclosed", "terms undisclosed")) {
            warnings.add("terms undisclosed");
        }
        if (reverseTakeover) {
            warnings.add("reverse takeover / RTO");
            warnings.add("not take-private");
        }
        if (containsAny(lower, "non-binding letter of intent", "non-binding loi", "letter of intent")) {
            warnings.add("non-binding LOI");
        }
        if (containsAny(lower, "definitive agreement expected", "subject to entering into definitive agreement",
                "will enter into definitive agreement", "definitive agreement is expected",
                "expected to enter into a definitive agreement", "execution of the definitive agreement")) {
            warnings.add("definitive agreement not signed");
        }
        if (containsAny(lower, "trading has been halted", "trading halted", "trading halt", "has been halted", "halted pending")) {
            warnings.add("trading halted");
        }
        if (containsAny(lower, "subject to shareholder approval", "requires shareholder approval",
                "approval of the shareholders", "approval of shareholders", "subject to regulatory approval",
                "regulatory approvals required", "approval required")) {
            warnings.add("shareholder/regulatory approvals required");
        }

        if (reverseTakeover) {
            positives.add("reverse takeover signal");
            return new RelevanceInsight(
                    DealRelevance.REVERSE_TAKEOVER,
                    cash ? Tradability.LOW : Tradability.NOT_TRADABLE,
                    "Reverse takeover/RTO structure detected; do not treat this as a cash take-private or public stock merger.",
                    warnings,
                    positives
            );
        }
        if (takePrivate && publicTarget && cash && perShare) {
            positives.add("take-private cash/per-share signal");
            return new RelevanceInsight(
                    DealRelevance.PUBLIC_TAKE_PRIVATE,
                    Tradability.HIGH,
                    "High-relevance public take-private style deal with cash/per-share language.",
                    warnings,
                    positives
            );
        }
        if (publicTarget && cash && perShare) {
            positives.add("public cash acquisition signal");
            return new RelevanceInsight(
                    DealRelevance.PUBLIC_CASH_ACQUISITION,
                    dealTerms.offerPrice() == null ? Tradability.MEDIUM : Tradability.HIGH,
                    "Public target with cash/per-share acquisition terms.",
                    warnings,
                    positives
            );
        }
        if (publicPublicMerger && stock) {
            warnings.add("not take-private");
            warnings.add("public-public merger");
            positives.add("public merger signal");
            return new RelevanceInsight(
                    DealRelevance.PUBLIC_PUBLIC_MERGER,
                    Tradability.MEDIUM,
                    "Real public-company merger, but not a take-private cash acquisition.",
                    warnings,
                    positives
            );
        }
        if (publicTarget && stock) {
            warnings.add("not take-private");
            positives.add("public stock merger signal");
            return new RelevanceInsight(
                    DealRelevance.PUBLIC_STOCK_MERGER,
                    Tradability.LOW,
                    "Public target stock-based merger; relevance is lower for cash/take-private strategy.",
                    warnings,
                    positives
            );
        }
        if (!publicTarget && (privateCompanySignal || lower.contains("acquire") || lower.contains("acquisition"))) {
            warnings.add("private-company acquisition");
            return new RelevanceInsight(
                    DealRelevance.PRIVATE_COMPANY_ACQUISITION,
                    dealTerms.offerPrice() == null ? Tradability.NOT_TRADABLE : Tradability.LOW,
                    "Looks like a real acquisition, but no public target signal was found.",
                    warnings,
                    positives
            );
        }
        if (!publicTarget && dealTerms.offerPrice() == null) {
            return new RelevanceInsight(
                    DealRelevance.NOT_TRADABLE,
                    Tradability.NOT_TRADABLE,
                    "No public target ticker or offer price found.",
                    warnings,
                    positives
            );
        }
        return new RelevanceInsight(
                DealRelevance.UNKNOWN,
                Tradability.UNKNOWN,
                "Not enough deterministic evidence to classify strategy relevance.",
                warnings,
                positives
        );
    }

    private boolean hasPublicTargetSignal(
            NewsArticleEntity article,
            DetectedEventEntity event,
            String lower,
            boolean privateTargetSignal,
            boolean publicBuyer
    ) {
        if (privateTargetSignal) {
            return false;
        }
        if (event != null && event.isTargetPublicCompany()
                && (event.getTargetMatchConfidence() == CompanyMatchConfidence.EXACT_TICKER
                || event.getTargetMatchConfidence() == CompanyMatchConfidence.EXACT_NAME)) {
            return true;
        }
        if (event != null && event.getTargetTicker() != null && !event.getTargetTicker().isBlank()
                && !"UNKNOWN".equalsIgnoreCase(event.getTargetTicker())
                && (event.getBuyerTicker() == null || !event.getTargetTicker().equalsIgnoreCase(event.getBuyerTicker()))
                && event.getTargetMatchConfidence() != CompanyMatchConfidence.PARTIAL_NAME) {
            return true;
        }
        if (article.getTicker() != null && !article.getTicker().isBlank() && !"UNKNOWN".equalsIgnoreCase(article.getTicker())
                && !publicBuyer) {
            return true;
        }
        if (publicBuyer) {
            return tickerCount(lower).count() >= 2;
        }
        return PUBLIC_TICKER.matcher(lower).find();
    }

    private boolean isFinancingOrDebtNoise(String lower) {
        return (lower.contains("senior notes") && (lower.contains("tender offer") || lower.contains("tender offers")))
                || (lower.contains("notes due") && (lower.contains("tender offer") || lower.contains("tender offers")))
                || containsAny(lower,
                "registered direct offering",
                "private placement",
                "at-the-market offering",
                "atm offering",
                "public offering",
                "follow-on offering",
                "senior notes tender offer",
                "tender offer for senior notes",
                "tender offer for notes",
                "debt tender offer",
                "exchange offer for notes",
                "share redemption",
                "redemption deadline",
                "extension vote",
                "extension meeting");
    }

    private boolean hasPrivateTargetSignal(DealTermsExtractionService.DealTerms dealTerms, String lower) {
        String target = dealTerms == null || dealTerms.targetCompany() == null
                ? ""
                : dealTerms.targetCompany().toLowerCase(Locale.ROOT);
        return target.contains(" llc")
                || target.endsWith("llc")
                || containsAny(target, "subsidiary", "business unit", "portfolio company")
                || containsAny(lower, "private company", "portfolio company",
                "terms were not disclosed", "terms undisclosed", "acquired from",
                "subsidiary", "business unit");
    }

    private boolean hasPublicBuyerSignal(DetectedEventEntity event, String lower) {
        if (event != null && event.isBuyerPublicCompany()
                && (event.getBuyerMatchConfidence() == CompanyMatchConfidence.EXACT_TICKER
                || event.getBuyerMatchConfidence() == CompanyMatchConfidence.EXACT_NAME)) {
            return true;
        }
        if (event != null && event.getAcquirer() != null && PUBLIC_TICKER.matcher(event.getAcquirer()).find()) {
            return true;
        }
        MatcherCount matcherCount = tickerCount(lower);
        return matcherCount.count() >= 2;
    }

    private MatcherCount tickerCount(String lower) {
        java.util.regex.Matcher matcher = PUBLIC_TICKER.matcher(lower);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return new MatcherCount(count);
    }

    private String combinedText(
            NewsArticleEntity article,
            DetectedEventEntity event,
            CandidateReviewInsightService.ReviewInsight reviewInsight,
            DealTermsExtractionService.DealTerms dealTerms
    ) {
        List<String> values = new ArrayList<>();
        values.add(article.getHeadline());
        values.add(article.getArticleText());
        values.add(article.getTicker());
        values.add(article.getCompanyName());
        if (event != null) {
            values.add(event.getTargetTicker());
            values.add(event.getTargetCik());
            values.add(event.getTargetCompany());
            values.add(event.getAcquirer());
            values.add(event.getBuyerTicker());
            values.add(event.getBuyerCik());
            values.add(event.getCompanyEnrichmentWarnings());
            values.add(event.getCashOrStock());
            values.add(event.getMatchedPositiveKeywords());
            values.add(event.getMatchedNegativeKeywords());
            values.add(event.getExplanation());
        }
        if (reviewInsight != null) {
            values.add(reviewInsight.reviewVerdict().name());
            values.add(String.join(" ", reviewInsight.reviewRiskFlags()));
            values.add(String.join(" ", reviewInsight.reviewPositiveSignals()));
        }
        if (dealTerms != null) {
            values.add(dealTerms.targetCompany());
            values.add(dealTerms.buyerCompany());
            values.add(dealTerms.paymentType().name());
            values.add(dealTerms.dealStatus().name());
            values.add(dealTerms.dealSummary());
            values.add(String.join(" ", dealTerms.dealWarnings()));
        }
        return String.join(" ", values.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }

    private boolean containsAny(String lower, String... phrases) {
        for (String phrase : phrases) {
            if (lower.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsReverseTakeover(String lower) {
        return containsAny(lower, "reverse takeover", "resulting issuer", "policy 5.2")
                || lower.matches(".*\\brto\\b.*");
    }

    public record RelevanceInsight(
            DealRelevance dealRelevance,
            Tradability tradability,
            String relevanceSummary,
            List<String> relevanceWarnings,
            List<String> relevancePositiveSignals
    ) {
    }

    private record MatcherCount(int count) {
    }
}
