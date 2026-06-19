package com.parsernews.service;

import com.parsernews.model.DealConfidence;
import com.parsernews.model.DealStatus;
import com.parsernews.model.PaymentType;
import com.parsernews.model.ReviewVerdict;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.ReviewStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DealTermsExtractionService {
    private static final Pattern OFFER_PRICE = Pattern.compile(
            "(?i)([$€£])\\s?([0-9]+(?:\\.[0-9]{1,4})?)\\s*(?:per share|a share|in cash)"
    );
    private static final Pattern RAISES_OFFER_TO_ACQUIRE = Pattern.compile(
            "(?i)^(.+?)\\s+raises\\s+(?:its\\s+)?offer\\s+to\\s+acquire\\s+(.+?)(?:\\s+to\\s+[$€£]|\\s+for\\s+[$€£]|\\s+in\\s+|\\s+-\\s+|:|$)"
    );
    private static final Pattern ENTERS_AGREEMENT_TO_ACQUIRE = Pattern.compile(
            "(?i)^(.+?)\\s+enters\\s+(?:into\\s+)?(?:a\\s+)?definitive\\s+agreement\\s+to\\s+acquire\\s+(.+?)(?:\\s+for\\s+|\\s+in\\s+|\\s+-\\s+|:|$)"
    );
    private static final Pattern TO_ACQUIRE = Pattern.compile(
            "(?i)^(.+?)\\s+to\\s+acquire\\s+(.+?)(?:\\s+for\\s+|\\s+in\\s+|\\s+-\\s+|:|$)"
    );
    private static final Pattern TO_BE_ACQUIRED_BY = Pattern.compile(
            "(?i)^(.+?)\\s+to\\s+be\\s+acquired\\s+by\\s+(.+?)(?:\\s+for\\s+|\\s+in\\s+|\\s+-\\s+|:|$)"
    );

    public DealTerms extract(
            NewsArticleEntity article,
            DetectedEventEntity event,
            CandidateReviewInsightService.ReviewInsight reviewInsight
    ) {
        String headline = safe(article.getHeadline());
        String text = combinedText(article, event, reviewInsight);
        String lower = text.toLowerCase(Locale.ROOT);

        CompanyPair companies = extractCompanies(headline);
        PriceMatch price = extractOfferPrice(text);
        PaymentType paymentType = paymentType(lower);
        DealStatus dealStatus = dealStatus(lower);

        List<String> warnings = new ArrayList<>();
        if (companies.targetCompany() == null) {
            warnings.add("target unknown");
        }
        if (companies.buyerCompany() == null) {
            warnings.add("buyer unknown");
        }
        if (price.offerPrice() == null) {
            warnings.add("offer price missing");
        }
        if (paymentType == PaymentType.UNKNOWN) {
            warnings.add("payment type unknown");
        }
        if (containsAny(lower, "non-binding letter of intent", "non-binding loi", "letter of intent")) {
            warnings.add("non-binding LOI");
        }
        if (definitiveAgreementNotSigned(lower)) {
            warnings.add("definitive agreement not signed");
        }
        if (article.getTicker() == null || article.getTicker().isBlank() || "UNKNOWN".equalsIgnoreCase(article.getTicker())) {
            warnings.add("ticker unknown");
        }
        if (reviewInsight != null && reviewInsight.reviewVerdict() == ReviewVerdict.LAW_FIRM_ALERT) {
            warnings.add("law firm alert");
        }
        if (event != null
                && event.getReviewStatus() == ReviewStatus.IGNORED
                && event.getCandidateStrength() == CandidateStrength.HIGH) {
            warnings.add("old reviewStatus IGNORED conflict");
        }

        DealConfidence confidence = confidence(event, reviewInsight, warnings, price.offerPrice(), dealStatus);
        return new DealTerms(
                companies.targetCompany(),
                companies.buyerCompany(),
                price.offerPrice(),
                price.offerCurrency(),
                paymentType,
                dealStatus,
                confidence,
                warnings,
                summary(companies, price, paymentType, dealStatus, confidence)
        );
    }

    private String combinedText(
            NewsArticleEntity article,
            DetectedEventEntity event,
            CandidateReviewInsightService.ReviewInsight reviewInsight
    ) {
        List<String> values = new ArrayList<>();
        values.add(article.getHeadline());
        values.add(article.getArticleText());
        if (event != null) {
            values.add(event.getCandidateReason());
            values.add(event.getMatchedPositiveKeywords());
            values.add(event.getMatchedNegativeKeywords());
            values.add(event.getFalsePositiveReasons());
            values.add(event.getExplanation());
            values.add(event.getTargetCompany());
            values.add(event.getAcquirer());
            values.add(event.getOfferPrice());
            values.add(event.getCashOrStock());
        }
        if (reviewInsight != null) {
            values.add(reviewInsight.reviewVerdict().name());
            values.add(reviewInsight.reviewSummary());
            values.add(String.join(" ", reviewInsight.reviewPositiveSignals()));
            values.add(String.join(" ", reviewInsight.reviewRiskFlags()));
        }
        return String.join(" ", values.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }

    private CompanyPair extractCompanies(String headline) {
        for (Pattern pattern : List.of(RAISES_OFFER_TO_ACQUIRE, ENTERS_AGREEMENT_TO_ACQUIRE, TO_ACQUIRE)) {
            Matcher matcher = pattern.matcher(headline);
            if (matcher.find()) {
                return new CompanyPair(cleanCompany(matcher.group(2)), cleanCompany(matcher.group(1)));
            }
        }
        Matcher acquiredBy = TO_BE_ACQUIRED_BY.matcher(headline);
        if (acquiredBy.find()) {
            return new CompanyPair(cleanCompany(acquiredBy.group(1)), cleanCompany(acquiredBy.group(2)));
        }
        return new CompanyPair(null, null);
    }

    private PriceMatch extractOfferPrice(String text) {
        Matcher matcher = OFFER_PRICE.matcher(text);
        BigDecimal bestPrice = null;
        String bestCurrency = null;
        while (matcher.find()) {
            BigDecimal price = new BigDecimal(matcher.group(2));
            if (bestPrice == null || price.compareTo(bestPrice) > 0) {
                bestPrice = price;
                bestCurrency = currency(matcher.group(1));
            }
        }
        return new PriceMatch(bestPrice, bestCurrency);
    }

    private PaymentType paymentType(String lower) {
        boolean cash = lower.contains("all-cash")
                || lower.contains("in cash")
                || lower.contains("cash consideration")
                || lower.contains("per share in cash");
        boolean stock = lower.contains("stock-for-stock")
                || lower.contains("shares of")
                || lower.contains("one share")
                || lower.matches(".*\\bone\\s+[a-z0-9& .'\\-]+\\s+share\\b.*")
                || lower.contains("ordinary shares")
                || lower.contains("common stock");
        if (cash && stock) {
            return PaymentType.CASH_AND_STOCK;
        }
        if (cash) {
            return PaymentType.CASH;
        }
        if (stock) {
            return PaymentType.STOCK;
        }
        return PaymentType.UNKNOWN;
    }

    private DealStatus dealStatus(String lower) {
        if (containsAny(lower, "non-binding letter of intent", "non-binding loi")) {
            return DealStatus.RUMOR_OR_EXPLORATION;
        }
        if (definitiveAgreementNotSigned(lower)) {
            return containsAny(lower, "proposed transaction", "proposed reverse takeover", "proposal")
                    ? DealStatus.PROPOSAL
                    : DealStatus.RUMOR_OR_EXPLORATION;
        }
        if (lower.contains("definitive merger agreement") || lower.contains("merger agreement")) {
            return DealStatus.MERGER_AGREEMENT;
        }
        if (lower.contains("definitive agreement")) {
            return DealStatus.DEFINITIVE_AGREEMENT;
        }
        if (lower.contains("tender offer")) {
            return DealStatus.TENDER_OFFER;
        }
        if (lower.contains("proposal")
                || lower.contains("offer to acquire")
                || lower.contains("raises offer")
                || lower.contains("to acquire")) {
            return DealStatus.PROPOSAL;
        }
        if (lower.contains("exploring") || lower.contains("considering") || lower.contains("rumor")) {
            return DealStatus.RUMOR_OR_EXPLORATION;
        }
        return DealStatus.UNKNOWN;
    }

    private boolean definitiveAgreementNotSigned(String lower) {
        return containsAny(lower, "definitive agreement expected", "definitive agreement is expected",
                "subject to entering into definitive agreement", "will enter into definitive agreement",
                "expected to enter into a definitive agreement", "execution of the definitive agreement");
    }

    private boolean containsAny(String lower, String... phrases) {
        for (String phrase : phrases) {
            if (lower.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private DealConfidence confidence(
            DetectedEventEntity event,
            CandidateReviewInsightService.ReviewInsight reviewInsight,
            List<String> warnings,
            BigDecimal offerPrice,
            DealStatus dealStatus
    ) {
        if (reviewInsight != null && reviewInsight.reviewVerdict() == ReviewVerdict.LAW_FIRM_ALERT) {
            return DealConfidence.LOW;
        }
        CandidateStrength strength = event == null ? CandidateStrength.NONE : event.getCandidateStrength();
        boolean highStatus = dealStatus == DealStatus.MERGER_AGREEMENT
                || dealStatus == DealStatus.DEFINITIVE_AGREEMENT
                || dealStatus == DealStatus.TENDER_OFFER;
        boolean proposal = dealStatus == DealStatus.PROPOSAL;
        boolean termsPresent = offerPrice != null
                && !warnings.contains("target unknown")
                && !warnings.contains("buyer unknown")
                && !warnings.contains("payment type unknown");

        if (strength == CandidateStrength.HIGH && highStatus && termsPresent) {
            return DealConfidence.HIGH;
        }
        if (strength == CandidateStrength.HIGH && (highStatus || proposal)) {
            return DealConfidence.MEDIUM;
        }
        if ((strength == CandidateStrength.MEDIUM || strength == CandidateStrength.HIGH)
                && dealStatus != DealStatus.UNKNOWN
                && dealStatus != DealStatus.RUMOR_OR_EXPLORATION) {
            return DealConfidence.MEDIUM;
        }
        if (strength == CandidateStrength.LOW || dealStatus == DealStatus.RUMOR_OR_EXPLORATION) {
            return DealConfidence.LOW;
        }
        return DealConfidence.UNKNOWN;
    }

    private String summary(
            CompanyPair companies,
            PriceMatch price,
            PaymentType paymentType,
            DealStatus dealStatus,
            DealConfidence confidence
    ) {
        String target = companies.targetCompany() == null ? "unknown target" : companies.targetCompany();
        String buyer = companies.buyerCompany() == null ? "unknown buyer" : companies.buyerCompany();
        String offer = price.offerPrice() == null ? "no offer price found" : price.offerCurrency() + " " + price.offerPrice();
        return "Detected " + dealStatus + " with " + confidence + " confidence: "
                + buyer + " / " + target + ", offer " + offer + ", payment " + paymentType + ".";
    }

    private String cleanCompany(String value) {
        String cleaned = safe(value)
                .replaceAll("(?i)\\s+(inc\\.|incorporated|corp\\.|corporation|ltd\\.|limited|plc|llc)\\b.*$", " $1")
                .replaceAll("(?i)\\s+(announces|announce|enters|entered|raises|raise|has|have)\\b.*$", "")
                .replaceAll("[,;]+$", "")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String currency(String symbol) {
        return switch (symbol) {
            case "$" -> "USD";
            case "€" -> "EUR";
            case "£" -> "GBP";
            default -> symbol;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record DealTerms(
            String targetCompany,
            String buyerCompany,
            BigDecimal offerPrice,
            String offerCurrency,
            PaymentType paymentType,
            DealStatus dealStatus,
            DealConfidence dealConfidence,
            List<String> dealWarnings,
            String dealSummary
    ) {
    }

    private record CompanyPair(String targetCompany, String buyerCompany) {
    }

    private record PriceMatch(BigDecimal offerPrice, String offerCurrency) {
    }
}
