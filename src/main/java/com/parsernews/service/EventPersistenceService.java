package com.parsernews.service;

import com.parsernews.model.AnalysisResult;
import com.parsernews.model.EventStatus;
import com.parsernews.model.EventType;
import com.parsernews.model.NewsEvent;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsArticleRepository;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceRepository;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class EventPersistenceService {
    private final NewsSourceRepository sourceRepository;
    private final NewsArticleRepository articleRepository;
    private final DetectedEventRepository eventRepository;
    private final FalsePositiveFilter falsePositiveFilter;
    private final CandidateScoringService candidateScoringService;

    public EventPersistenceService(
            NewsSourceRepository sourceRepository,
            NewsArticleRepository articleRepository,
            DetectedEventRepository eventRepository,
            FalsePositiveFilter falsePositiveFilter,
            CandidateScoringService candidateScoringService
    ) {
        this.sourceRepository = sourceRepository;
        this.articleRepository = articleRepository;
        this.eventRepository = eventRepository;
        this.falsePositiveFilter = falsePositiveFilter;
        this.candidateScoringService = candidateScoringService;
    }

    @Transactional
    public void save(NewsEvent newsEvent, AnalysisResult analysisResult) {
        NewsSourceEntity source = sourceRepository.findByName(newsEvent.source())
                .orElseGet(() -> sourceRepository.save(new NewsSourceEntity(
                        newsEvent.source(),
                        sourceType(newsEvent),
                        newsEvent.sourceUrl()
                )));

        String urlHash = urlHash(newsEvent.sourceUrl());
        NewsArticleEntity article = articleRepository.findByUrlHash(urlHash)
                .orElseGet(() -> articleRepository.save(new NewsArticleEntity(
                        source,
                        urlHash,
                        newsEvent.ticker(),
                        newsEvent.companyName(),
                        newsEvent.headline(),
                        newsEvent.body(),
                        newsEvent.sourceUrl(),
                        newsEvent.publishedAt()
                )));

        eventRepository.findByArticle(article).ifPresent(existingEvent -> {
            eventRepository.delete(existingEvent);
            eventRepository.flush();
        });

        if (!shouldPersistDetectedEvent(analysisResult)) {
            return;
        }

        CandidateScoringService.CandidateScore candidateScore = candidateScoringService.score(
                newsEvent.headline(),
                newsEvent.body()
        );
        eventRepository.save(new DetectedEventEntity(
                article,
                mapEventType(analysisResult.eventType()),
                mapReviewStatus(analysisResult.status()),
                analysisResult.score(),
                newsEvent.companyName(),
                bestValue(analysisResult.targetTicker(), newsEvent.ticker()),
                analysisResult.acquirer(),
                analysisResult.offerPrice(),
                analysisResult.cashOrStock(),
                analysisResult.premiumPercent(),
                candidateScore.score(),
                candidateScore.strength(),
                candidateScore.reason(),
                join(analysisResult.matchedPositiveKeywords()),
                join(analysisResult.matchedNegativeKeywords()),
                join(falsePositiveFilter.reasons(newsEvent.fullText())),
                analysisResult.reason()
        ));
    }

    private boolean shouldPersistDetectedEvent(AnalysisResult result) {
        return result.eventType() != EventType.UNKNOWN || result.score() != 0;
    }

    private NewsSourceType sourceType(NewsEvent event) {
        String source = event.source().toLowerCase(Locale.ROOT);
        String url = event.sourceUrl().toLowerCase(Locale.ROOT);
        if (url.contains("sec.gov") || source.contains("sec")) {
            return NewsSourceType.SEC;
        }
        if (source.contains("mock")) {
            return NewsSourceType.MOCK;
        }
        if (source.contains("historical") || source.contains("archive")) {
            return NewsSourceType.HISTORICAL;
        }
        if (url.startsWith("https://")) {
            return NewsSourceType.RSS;
        }
        return NewsSourceType.OTHER;
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

    private ReviewStatus mapReviewStatus(EventStatus status) {
        return ReviewStatus.valueOf(status.name());
    }

    private String join(List<String> values) {
        return String.join("|", values);
    }

    private String bestValue(String extractedValue, String fallback) {
        if (extractedValue != null && !extractedValue.isBlank()) {
            return extractedValue;
        }
        return fallback;
    }

    public static String urlHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
