package com.parsernews.service;

import com.parsernews.model.AnalysisResult;
import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealStage;
import com.parsernews.model.DealTiming;
import com.parsernews.model.EventType;
import com.parsernews.model.NewsEvent;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import com.parsernews.util.ArticleTextCleaner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SourceEvaluationPreviewService {
    private static final int DEFAULT_MAX_ITEMS = 50;
    private static final int MAX_ALLOWED_ITEMS = 100;
    private static final Pattern MARKDOWN_URL = Pattern.compile("^\\[(https://[^\\]]+)]\\([^)]*\\)$");

    private final RuleBasedNewsAnalyzer analyzer;
    private final CandidateScoringService candidateScoringService;
    private final CandidateReviewInsightService reviewInsightService;
    private final DealTermsExtractionService dealTermsExtractionService;
    private final DealRelevanceService dealRelevanceService;
    private final DealStageDetectionService dealStageDetectionService;
    private final AlertEligibilityService alertEligibilityService;
    private final FalsePositiveFilter falsePositiveFilter;
    private final HttpClient httpClient;

    @Autowired
    public SourceEvaluationPreviewService(
            RuleBasedNewsAnalyzer analyzer,
            CandidateScoringService candidateScoringService,
            CandidateReviewInsightService reviewInsightService,
            DealTermsExtractionService dealTermsExtractionService,
            DealRelevanceService dealRelevanceService,
            DealStageDetectionService dealStageDetectionService,
            AlertEligibilityService alertEligibilityService,
            FalsePositiveFilter falsePositiveFilter
    ) {
        this(
                analyzer,
                candidateScoringService,
                reviewInsightService,
                dealTermsExtractionService,
                dealRelevanceService,
                dealStageDetectionService,
                alertEligibilityService,
                falsePositiveFilter,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build()
        );
    }

    SourceEvaluationPreviewService(
            RuleBasedNewsAnalyzer analyzer,
            CandidateScoringService candidateScoringService,
            CandidateReviewInsightService reviewInsightService,
            DealTermsExtractionService dealTermsExtractionService,
            DealRelevanceService dealRelevanceService,
            DealStageDetectionService dealStageDetectionService,
            AlertEligibilityService alertEligibilityService,
            FalsePositiveFilter falsePositiveFilter,
            HttpClient httpClient
    ) {
        this.analyzer = analyzer;
        this.candidateScoringService = candidateScoringService;
        this.reviewInsightService = reviewInsightService;
        this.dealTermsExtractionService = dealTermsExtractionService;
        this.dealRelevanceService = dealRelevanceService;
        this.dealStageDetectionService = dealStageDetectionService;
        this.alertEligibilityService = alertEligibilityService;
        this.falsePositiveFilter = falsePositiveFilter;
        this.httpClient = httpClient;
    }

    public SourceEvaluationPreviewResponse preview(SourceEvaluationPreviewRequest request) {
        String sourceName = isBlank(request.name()) ? "Source preview" : request.name().trim();
        String url = normalizeUrl(request.url());
        int maxItems = normalizeMaxItems(request.maxItems());
        List<String> errors = new ArrayList<>();
        List<NewsEvent> events;
        try {
            events = fetchRss(sourceName, url, maxItems);
        } catch (RuntimeException exception) {
            errors.add(exception.getMessage());
            return new SourceEvaluationPreviewResponse(
                    sourceName,
                    url,
                    0,
                    0,
                    0,
                    0,
                    errors,
                    Recommendation.DISABLE,
                    List.of()
            );
        }

        List<PreviewItem> items = events.stream()
                .map(this::previewItem)
                .toList();
        long candidateCount = items.stream()
                .filter(item -> item.priority() == CandidateStrength.HIGH || item.priority() == CandidateStrength.MEDIUM)
                .count();
        long strictCandidateCount = items.stream().filter(this::isStrictCandidate).count();
        long noiseCount = items.stream()
                .filter(item -> item.priority() == CandidateStrength.NONE
                        || item.tradability() == Tradability.NOT_TRADABLE
                        || isExcludedRelevance(item.dealRelevance())
                        || item.dealTiming() == DealTiming.NOISE
                        || item.dealTiming() == DealTiming.LATE_STAGE
                        || item.dealTiming() == DealTiming.POST_CLOSE)
                .count();
        return new SourceEvaluationPreviewResponse(
                sourceName,
                url,
                events.size(),
                candidateCount,
                strictCandidateCount,
                noiseCount,
                errors,
                recommendation(events.size(), candidateCount, strictCandidateCount),
                items
        );
    }

    private PreviewItem previewItem(NewsEvent event) {
        AnalysisResult analysis = analyzer.analyze(event);
        CandidateScoringService.CandidateScore candidateScore = candidateScoringService.score(event.headline(), event.body());
        NewsSourceEntity source = new NewsSourceEntity(event.source(), NewsSourceType.RSS, event.sourceUrl());
        NewsArticleEntity article = new NewsArticleEntity(
                source,
                "preview-" + Math.abs((event.sourceUrl() == null ? event.headline() : event.sourceUrl()).hashCode()),
                event.ticker(),
                event.companyName(),
                event.headline(),
                event.body(),
                event.sourceUrl(),
                event.publishedAt()
        );
        DetectedEventEntity detectedEvent = new DetectedEventEntity(
                article,
                mapEventType(analysis.eventType()),
                ReviewStatus.valueOf(analysis.status().name()),
                analysis.score(),
                event.companyName(),
                bestValue(analysis.targetTicker(), event.ticker()),
                analysis.acquirer(),
                analysis.offerPrice(),
                analysis.cashOrStock(),
                analysis.premiumPercent(),
                candidateScore.score(),
                candidateScore.strength(),
                candidateScore.reason(),
                false,
                "Source evaluation preview only.",
                join(analysis.matchedPositiveKeywords()),
                join(analysis.matchedNegativeKeywords()),
                join(falsePositiveFilter.reasons(event.fullText())),
                analysis.reason()
        );

        CandidateReviewInsightService.ReviewInsight reviewInsight = reviewInsightService.insight(article, detectedEvent);
        DealTermsExtractionService.DealTerms dealTerms = dealTermsExtractionService.extract(article, detectedEvent, reviewInsight);
        DealRelevanceService.RelevanceInsight relevance = dealRelevanceService.assess(article, detectedEvent, reviewInsight, dealTerms);
        DealStageDetectionService.StageInsight stage = dealStageDetectionService.detect(article, detectedEvent, dealTerms, reviewInsight, relevance);
        AlertEligibilityService.AlertEligibility alertEligibility = alertEligibilityService.evaluate(detectedEvent);

        return new PreviewItem(
                event.headline(),
                event.sourceUrl(),
                event.publishedAt(),
                candidateScore.strength(),
                relevance.dealRelevance(),
                relevance.tradability(),
                stage.dealStage(),
                stage.dealTiming(),
                alertEligibility.eligible()
        );
    }

    private boolean isStrictCandidate(PreviewItem item) {
        return (item.priority() == CandidateStrength.HIGH || item.priority() == CandidateStrength.MEDIUM)
                && (item.tradability() == Tradability.HIGH || item.tradability() == Tradability.MEDIUM)
                && (item.dealRelevance() == DealRelevance.PUBLIC_CASH_ACQUISITION
                || item.dealRelevance() == DealRelevance.PUBLIC_TAKE_PRIVATE
                || item.dealRelevance() == DealRelevance.PUBLIC_PUBLIC_MERGER)
                && (item.dealTiming() == DealTiming.EARLY || item.dealTiming() == DealTiming.MID_STAGE)
                && !isExcludedRelevance(item.dealRelevance());
    }

    private boolean isExcludedRelevance(DealRelevance relevance) {
        return relevance == DealRelevance.LAW_FIRM_OR_SHAREHOLDER_ALERT
                || relevance == DealRelevance.PRIVATE_COMPANY_ACQUISITION
                || relevance == DealRelevance.REVERSE_TAKEOVER
                || relevance == DealRelevance.NOT_TRADABLE;
    }

    private Recommendation recommendation(int fetchedCount, long candidateCount, long strictCandidateCount) {
        if (strictCandidateCount >= 1) {
            return Recommendation.KEEP;
        }
        if (fetchedCount > 10 && candidateCount == 0) {
            return Recommendation.DISABLE;
        }
        return Recommendation.NEEDS_REVIEW;
    }

    private List<NewsEvent> fetchRss(String sourceName, String url, int maxItems) {
        URI uri = URI.create(url);
        validateUri(uri);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/rss+xml, application/xml, text/xml")
                    .header("User-Agent", "ParserNews/1.0 source-evaluation-preview")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("RSS feed returned status " + response.statusCode() + ".");
            }
            return parseRss(response.body(), sourceName, url, maxItems);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading RSS feed.", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read RSS feed: " + exception.getMessage(), exception);
        }
    }

    private List<NewsEvent> parseRss(String xml, String sourceName, String feedUrl, int maxItems) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xml)));
        NodeList items = document.getElementsByTagName("item");
        int limit = Math.min(items.getLength(), maxItems);
        List<NewsEvent> events = new ArrayList<>();
        for (int index = 0; index < limit; index++) {
            Element item = (Element) items.item(index);
            String headline = cleanText(textOfFirst(item, "title", ""));
            if (headline.isBlank()) {
                continue;
            }
            String body = cleanText(textOfFirst(item, "description", ""));
            String link = cleanText(textOfFirst(item, "link", feedUrl));
            events.add(new NewsEvent(
                    "UNKNOWN",
                    guessCompanyName(headline),
                    headline,
                    body,
                    sourceName,
                    link,
                    parsePublishedAt(textOfFirst(item, "pubDate", "")),
                    null,
                    null,
                    null
            ));
        }
        return events;
    }

    private String normalizeUrl(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Source URL is required.");
        }
        String trimmed = value.trim();
        Matcher matcher = MARKDOWN_URL.matcher(trimmed);
        if (matcher.matches()) {
            trimmed = matcher.group(1);
        }
        try {
            validateUri(URI.create(trimmed));
            return trimmed;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Source URL must be a valid HTTPS URL.");
        }
    }

    private void validateUri(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Only HTTPS RSS feeds are allowed.");
        }
    }

    private int normalizeMaxItems(Integer maxItems) {
        if (maxItems == null || maxItems <= 0) {
            return DEFAULT_MAX_ITEMS;
        }
        return Math.min(maxItems, MAX_ALLOWED_ITEMS);
    }

    private String textOfFirst(Element element, String tagName, String fallback) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return fallback;
        }
        return nodes.item(0).getTextContent();
    }

    private Instant parsePublishedAt(String value) {
        String cleaned = cleanText(value);
        if (cleaned.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(cleaned, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String cleanText(String value) {
        return ArticleTextCleaner.cleanText(value);
    }

    private String guessCompanyName(String headline) {
        String trimmed = headline.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int announcesIndex = lower.indexOf(" announces ");
        if (announcesIndex > 0) {
            return trimmed.substring(0, announcesIndex).trim();
        }
        int entersIndex = lower.indexOf(" enters ");
        if (entersIndex > 0) {
            return trimmed.substring(0, entersIndex).trim();
        }
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80).trim();
    }

    private DetectedEventType mapEventType(EventType eventType) {
        return switch (eventType) {
            case TAKE_PRIVATE_CONFIRMED -> DetectedEventType.GOING_PRIVATE;
            case TAKE_PRIVATE_RUMOR, ACQUISITION_RUMOR -> DetectedEventType.PROPOSAL;
            case CONFIRMED_DEAL -> DetectedEventType.DEFINITIVE_AGREEMENT;
            case MERGER_CONFIRMED -> DetectedEventType.MERGER;
            case ACQUISITION_CONFIRMED -> DetectedEventType.ACQUISITION;
            case TENDER_OFFER -> DetectedEventType.TENDER_OFFER;
            case DEBT_TENDER_OFFER -> DetectedEventType.DEBT_TENDER_OFFER;
            case STRATEGIC_ALTERNATIVES -> DetectedEventType.STRATEGIC_ALTERNATIVES;
            case OFFERING_OR_DILUTION -> DetectedEventType.OFFERING_OR_DILUTION;
            case BANKRUPTCY_RISK -> DetectedEventType.BANKRUPTCY_RISK;
            case NOISE, UNKNOWN -> DetectedEventType.OTHER;
        };
    }

    private String join(List<String> values) {
        return values == null ? "" : String.join("|", values);
    }

    private String bestValue(String extractedValue, String fallback) {
        if (!isBlank(extractedValue)) {
            return extractedValue;
        }
        return fallback;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum Recommendation {
        KEEP,
        DISABLE,
        NEEDS_REVIEW
    }

    public record SourceEvaluationPreviewRequest(
            String name,
            String url,
            Integer maxItems
    ) {
    }

    public record SourceEvaluationPreviewResponse(
            String sourceName,
            String url,
            int fetchedCount,
            long candidateCount,
            long strictCandidateCount,
            long noiseCount,
            List<String> errors,
            Recommendation recommendation,
            List<PreviewItem> previewItems
    ) {
    }

    public record PreviewItem(
            String title,
            String url,
            Instant publishedAt,
            CandidateStrength priority,
            DealRelevance dealRelevance,
            Tradability tradability,
            DealStage dealStage,
            DealTiming dealTiming,
            boolean alertEligible
    ) {
    }
}
