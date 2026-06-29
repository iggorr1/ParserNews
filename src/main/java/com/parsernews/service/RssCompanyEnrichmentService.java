package com.parsernews.service;

import com.parsernews.persistence.CompanyMatchConfidence;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.NewsArticleEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RssCompanyEnrichmentService {
    private static final Pattern EXCHANGE_TICKER = Pattern.compile(
            "(?i)(?:NASDAQ|NYSE|NYSEAMERICAN|NYSE\\s+American|AMEX|OTC|OTCQB|OTCQX|OTCMKTS|TSX|TSXV)\\s*:\\s*([A-Z][A-Z0-9.-]{0,9})"
    );
    private static final Pattern PAREN_TICKER = Pattern.compile("(?i)\\(([A-Z]{1,5})\\)");

    private final SecCompanyLookupService companyLookupService;
    private final CandidateReviewInsightService reviewInsightService;
    private final DealTermsExtractionService dealTermsExtractionService;

    public RssCompanyEnrichmentService(
            SecCompanyLookupService companyLookupService,
            CandidateReviewInsightService reviewInsightService,
            DealTermsExtractionService dealTermsExtractionService
    ) {
        this.companyLookupService = companyLookupService;
        this.reviewInsightService = reviewInsightService;
        this.dealTermsExtractionService = dealTermsExtractionService;
    }

    public CompanyEnrichment enrich(NewsArticleEntity article, DetectedEventEntity event) {
        CandidateReviewInsightService.ReviewInsight insight = reviewInsightService.insight(article, event);
        DealTermsExtractionService.DealTerms dealTerms = dealTermsExtractionService.extract(article, event, insight);
        String text = article.getHeadline() + " " + nullToBlank(article.getArticleText());
        String targetCompany = firstNonBlank(dealTerms.targetCompany(), event.getTargetCompany(), article.getCompanyName());
        String buyerCompany = firstNonBlank(dealTerms.buyerCompany(), event.getAcquirer());
        boolean privateTargetSignal = hasPrivateTargetSignal(targetCompany);
        String buyerTicker = tickerNearCompany(text, buyerCompany);
        String targetTicker = privateTargetSignal ? null : targetTicker(text, targetCompany, buyerCompany, buyerTicker, event.getTargetTicker());

        CompanyRoleEnrichment target = privateTargetSignal
                ? new CompanyRoleEnrichment(null, null, false, CompanyMatchConfidence.NONE)
                : resolveRole(targetTicker, targetCompany);
        CompanyRoleEnrichment buyer = resolveRole(buyerTicker, buyerCompany);

        // Cross-check: buyer may have grabbed the target's ticker from the headline window
        // (e.g. "Merck KGaA Agrees to Acquire Bio-Techne (NASDAQ: TECH)" — TECH is within
        // 120 chars of Merck so it gets assigned to buyer). If the buyer's ticker resolves
        // to the target company rather than the buyer company, reassign it.
        if (!privateTargetSignal && buyer.ticker() != null && target.ticker() == null) {
            boolean buyerTickerBelongsToTarget = companyLookupService
                    .findBestMatch(buyer.ticker(), targetCompany)
                    .filter(m -> m.confidence() == CompanyMatchConfidence.EXACT_TICKER
                            || m.confidence() == CompanyMatchConfidence.EXACT_NAME)
                    .isPresent();
            if (buyerTickerBelongsToTarget) {
                target = resolveRole(buyer.ticker(), targetCompany);
                buyer = new CompanyRoleEnrichment(null, null, false, CompanyMatchConfidence.NONE);
            }
        }

        List<String> warnings = new ArrayList<>();
        if (buyer.publicCompany() && !target.publicCompany()) {
            warnings.add("buyer resolved but target not resolved");
            warnings.add("do not infer public target from public buyer");
        }
        if (privateTargetSignal) {
            warnings.add("private target signal");
        }
        if (target.matchConfidence() == CompanyMatchConfidence.PARTIAL_NAME) {
            warnings.add("target partial name match is weak evidence");
        }
        if (buyer.matchConfidence() == CompanyMatchConfidence.PARTIAL_NAME) {
            warnings.add("buyer partial name match is weak evidence");
        }
        return new CompanyEnrichment(target, buyer, warnings);
    }

    private String targetTicker(
            String text,
            String targetCompany,
            String buyerCompany,
            String buyerTicker,
            String eventTargetTicker
    ) {
        String nearTarget = tickerNearCompanyExcluding(text, targetCompany, buyerTicker);
        if (nearTarget != null && !isSameTicker(nearTarget, buyerTicker)) {
            return nearTarget;
        }
        String fallback = firstNonBlank(eventTargetTicker);
        if (sameCompany(targetCompany, buyerCompany)) {
            return firstNonBlank(nearTarget, fallback);
        }
        return isSameTicker(fallback, buyerTicker) ? null : fallback;
    }

    private CompanyRoleEnrichment resolveRole(String explicitTicker, String companyName) {
        Optional<SecCompanyLookupService.CompanyLookupMatch> match = companyLookupService.findBestMatch(explicitTicker, companyName);
        if (match.isEmpty()) {
            return new CompanyRoleEnrichment(null, null, false, CompanyMatchConfidence.NONE);
        }
        SecCompanyLookupService.CompanyLookupMatch value = match.get();
        return new CompanyRoleEnrichment(
                value.ticker(),
                value.cik(),
                value.publicCompanyEvidence(),
                value.confidence()
        );
    }

    private List<String> explicitTickers(String text) {
        List<String> tickers = new ArrayList<>();
        Matcher exchangeMatcher = EXCHANGE_TICKER.matcher(text);
        while (exchangeMatcher.find()) {
            addTicker(tickers, exchangeMatcher.group(1));
        }
        Matcher parenMatcher = PAREN_TICKER.matcher(text);
        while (parenMatcher.find()) {
            addTicker(tickers, parenMatcher.group(1));
        }
        return tickers;
    }

    private String tickerNearCompany(String text, String companyName) {
        return tickerNearCompanyExcluding(text, companyName, null);
    }

    private String tickerNearCompanyExcluding(String text, String companyName, String excludedTicker) {
        if (text == null || companyName == null || companyName.isBlank()) {
            return null;
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerCompany = companyName.toLowerCase(Locale.ROOT);
        int searchFrom = 0;
        while (searchFrom < lowerText.length()) {
            int companyIndex = lowerText.indexOf(lowerCompany, searchFrom);
            if (companyIndex < 0) {
                return null;
            }
            int start = Math.max(0, companyIndex);
            int end = Math.min(text.length(), companyIndex + companyName.length() + 120);
            String nearby = text.substring(start, end);
            List<String> nearbyTickers = explicitTickers(nearby);
            for (String ticker : nearbyTickers) {
                if (!isSameTicker(ticker, excludedTicker)) {
                    return ticker;
                }
            }
            searchFrom = companyIndex + lowerCompany.length();
        }
        return null;
    }

    private boolean hasPrivateTargetSignal(String companyName) {
        if (companyName == null) {
            return false;
        }
        String normalized = companyName.toLowerCase(Locale.ROOT);
        return normalized.contains(" llc")
                || normalized.endsWith("llc")
                || normalized.contains(" limited liability")
                || normalized.contains("subsidiary")
                || normalized.contains("business unit")
                || normalized.contains("facility")
                || normalized.contains("assets");
    }

    private boolean isSameTicker(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        return first.replaceAll("[^A-Za-z0-9]", "")
                .equalsIgnoreCase(second.replaceAll("[^A-Za-z0-9]", ""));
    }

    private boolean sameCompany(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        String normalizedFirst = first.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        String normalizedSecond = second.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        return !normalizedFirst.isBlank() && normalizedFirst.equals(normalizedSecond);
    }

    private void addTicker(List<String> tickers, String ticker) {
        String normalized = ticker == null ? "" : ticker.trim().toUpperCase(Locale.ROOT);
        if (normalized.matches("[A-Z][A-Z0-9.-]{0,9}") && !tickers.contains(normalized)) {
            tickers.add(normalized);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"UNKNOWN".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return null;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    public record CompanyEnrichment(
            CompanyRoleEnrichment target,
            CompanyRoleEnrichment buyer,
            List<String> warnings
    ) {
    }

    public record CompanyRoleEnrichment(
            String ticker,
            String cik,
            boolean publicCompany,
            CompanyMatchConfidence matchConfidence
    ) {
    }
}
