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
        String targetTicker = privateTargetSignal ? null : firstNonBlank(event.getTargetTicker(), tickerNearCompany(text, targetCompany));
        String buyerTicker = tickerNearCompany(text, buyerCompany);

        CompanyRoleEnrichment target = privateTargetSignal
                ? new CompanyRoleEnrichment(null, null, false, CompanyMatchConfidence.NONE)
                : resolveRole(targetTicker, targetCompany);
        CompanyRoleEnrichment buyer = resolveRole(
                buyerTicker,
                buyerCompany
        );

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
        if (text == null || companyName == null || companyName.isBlank()) {
            return null;
        }
        int companyIndex = text.toLowerCase(Locale.ROOT).indexOf(companyName.toLowerCase(Locale.ROOT));
        if (companyIndex < 0) {
            return null;
        }
        int start = Math.max(0, companyIndex);
        int end = Math.min(text.length(), companyIndex + companyName.length() + 120);
        String nearby = text.substring(start, end);
        List<String> nearbyTickers = explicitTickers(nearby);
        return nearbyTickers.isEmpty() ? null : nearbyTickers.getFirst();
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
