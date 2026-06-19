package com.parsernews.web;

import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsArticleRepository;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import com.parsernews.service.CandidateReviewInsightService;
import com.parsernews.service.DealRelevanceService;
import com.parsernews.service.DealStageDetectionService;
import com.parsernews.service.DealTermsExtractionService;
import com.parsernews.util.ArticleTextCleaner;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestController
public class ArticleController {
    private final NewsArticleRepository articleRepository;
    private final DetectedEventRepository eventRepository;
    private final CandidateReviewInsightService reviewInsightService;
    private final DealTermsExtractionService dealTermsExtractionService;
    private final DealRelevanceService dealRelevanceService;
    private final DealStageDetectionService dealStageDetectionService;

    public ArticleController(
            NewsArticleRepository articleRepository,
            DetectedEventRepository eventRepository,
            CandidateReviewInsightService reviewInsightService,
            DealTermsExtractionService dealTermsExtractionService,
            DealRelevanceService dealRelevanceService,
            DealStageDetectionService dealStageDetectionService
    ) {
        this.articleRepository = articleRepository;
        this.eventRepository = eventRepository;
        this.reviewInsightService = reviewInsightService;
        this.dealTermsExtractionService = dealTermsExtractionService;
        this.dealRelevanceService = dealRelevanceService;
        this.dealStageDetectionService = dealStageDetectionService;
    }

    @GetMapping("/api/articles")
    @Transactional(readOnly = true)
    public List<ArticleListResponse> articles(
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "false") boolean candidateOnly,
            @RequestParam(defaultValue = "200") int limit
    ) {
        if (candidateOnly) {
            return candidates(limit);
        }
        return articleRepository.findTop200ByOrderByCollectedAtDesc().stream()
                .filter(article -> source == null || article.getSource().getName().toLowerCase().contains(source.toLowerCase()))
                .limit(normalizedLimit(limit))
                .map(article -> ArticleListResponse.from(
                        article,
                        eventRepository.findByArticle(article).orElse(null),
                        reviewInsightService,
                        dealTermsExtractionService,
                        dealRelevanceService,
                        dealStageDetectionService
                ))
                .toList();
    }

    @GetMapping("/api/articles/candidates")
    @Transactional(readOnly = true)
    public List<ArticleListResponse> candidateArticles(@RequestParam(defaultValue = "200") int limit) {
        return candidates(limit);
    }

    @GetMapping("/api/articles/{id}")
    @Transactional(readOnly = true)
    public ArticleDetailResponse article(@PathVariable Long id) {
        NewsArticleEntity article = articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));
        return ArticleDetailResponse.from(article, eventRepository.findByArticle(article).orElse(null), reviewInsightService, dealTermsExtractionService, dealRelevanceService, dealStageDetectionService);
    }

    @PatchMapping("/api/articles/{id}/manual-review")
    @Transactional
    public ArticleDetailResponse updateManualReview(
            @PathVariable Long id,
            @RequestBody ManualReviewRequest request
    ) {
        NewsArticleEntity article = articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));
        DetectedEventEntity event = eventRepository.findByArticle(article)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Article has no detected event to review"));
        event.updateManualReview(request.status(), request.reason(), request.note());
        return ArticleDetailResponse.from(article, event, reviewInsightService, dealTermsExtractionService, dealRelevanceService, dealStageDetectionService);
    }

    private List<ArticleListResponse> candidates(int limit) {
        return eventRepository.findTop200ByOrderByDetectedAtDesc().stream()
                .filter(event -> event.getCandidateStrength() != CandidateStrength.NONE)
                .sorted(Comparator.comparingInt(DetectedEventEntity::getCandidateScore).reversed()
                        .thenComparing(DetectedEventEntity::getDetectedAt, Comparator.reverseOrder()))
                .limit(normalizedLimit(limit))
                .map(event -> ArticleListResponse.from(event.getArticle(), event, reviewInsightService, dealTermsExtractionService, dealRelevanceService, dealStageDetectionService))
                .toList();
    }

    private int normalizedLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 200);
    }

    public record ArticleListResponse(
            Long id,
            String title,
            String url,
            String source,
            String host,
            NewsSourceType sourceType,
            Instant publishedAt,
            Instant discoveredAt,
            boolean candidate,
            DetectedEventType eventType,
            ReviewStatus reviewStatus,
            int candidateScore,
            CandidateStrength candidateStrength,
            String candidateReason,
            ManualReviewStatus manualReviewStatus,
            ManualReviewReason manualReviewReason,
            String manualReviewNote,
            Instant manualReviewedAt,
            boolean alertEligible,
            Instant alertQueuedAt,
            String alertReason,
            String matchedPositiveKeywords,
            String matchedNegativeKeywords,
            com.parsernews.model.ReviewVerdict reviewVerdict,
            String reviewSummary,
            List<String> reviewRiskFlags,
            List<String> reviewPositiveSignals,
            String suggestedAction,
            String targetCompany,
            String buyerCompany,
            BigDecimal offerPrice,
            String offerCurrency,
            com.parsernews.model.PaymentType paymentType,
            com.parsernews.model.DealStatus dealStatus,
            com.parsernews.model.DealConfidence dealConfidence,
            List<String> dealWarnings,
            String dealSummary,
            com.parsernews.model.DealRelevance dealRelevance,
            com.parsernews.model.Tradability tradability,
            String relevanceSummary,
            List<String> relevanceWarnings,
            List<String> relevancePositiveSignals,
            com.parsernews.model.DealStage dealStage,
            com.parsernews.model.DealTiming dealTiming,
            String stageSummary,
            List<String> stageWarnings,
            List<String> stagePositiveSignals,
            String snippet,
            boolean hasFullText,
            int fullTextLength
    ) {
        static ArticleListResponse from(
                NewsArticleEntity article,
                DetectedEventEntity event,
                CandidateReviewInsightService reviewInsightService,
                DealTermsExtractionService dealTermsExtractionService,
                DealRelevanceService dealRelevanceService,
                DealStageDetectionService dealStageDetectionService
        ) {
            String text = article.getArticleText();
            CandidateReviewInsightService.ReviewInsight insight = reviewInsightService.insight(article, event);
            DealTermsExtractionService.DealTerms dealTerms = dealTermsExtractionService.extract(article, event, insight);
            DealRelevanceService.RelevanceInsight relevance = dealRelevanceService.assess(article, event, insight, dealTerms);
            DealStageDetectionService.StageInsight stage = dealStageDetectionService.detect(article, event, dealTerms, insight, relevance);
            return new ArticleListResponse(
                    article.getId(),
                    article.getHeadline(),
                    article.getUrl(),
                    article.getSource().getName(),
                    extractHost(article.getUrl()),
                    article.getSource().getType(),
                    article.getPublishedAt(),
                    article.getCollectedAt(),
                    event != null,
                    event == null ? null : event.getEventType(),
                    event == null ? null : event.getReviewStatus(),
                    event == null ? 0 : event.getCandidateScore(),
                    event == null ? CandidateStrength.NONE : event.getCandidateStrength(),
                    event == null ? "No detected M&A candidate event." : event.getCandidateReason(),
                    event == null ? ManualReviewStatus.PENDING : event.getManualReviewStatus(),
                    event == null ? null : event.getManualReviewReason(),
                    event == null ? null : event.getManualReviewNote(),
                    event == null ? null : event.getManualReviewedAt(),
                    event != null && event.isAlertEligible(),
                    event == null ? null : event.getAlertQueuedAt(),
                    event == null ? null : event.getAlertReason(),
                    event == null ? null : event.getMatchedPositiveKeywords(),
                    event == null ? null : event.getMatchedNegativeKeywords(),
                    insight.reviewVerdict(),
                    insight.reviewSummary(),
                    insight.reviewRiskFlags(),
                    insight.reviewPositiveSignals(),
                    insight.suggestedAction(),
                    dealTerms.targetCompany(),
                    dealTerms.buyerCompany(),
                    dealTerms.offerPrice(),
                    dealTerms.offerCurrency(),
                    dealTerms.paymentType(),
                    dealTerms.dealStatus(),
                    dealTerms.dealConfidence(),
                    dealTerms.dealWarnings(),
                    dealTerms.dealSummary(),
                    relevance.dealRelevance(),
                    relevance.tradability(),
                    relevance.relevanceSummary(),
                    relevance.relevanceWarnings(),
                    relevance.relevancePositiveSignals(),
                    stage.dealStage(),
                    stage.dealTiming(),
                    stage.stageSummary(),
                    stage.stageWarnings(),
                    stage.stagePositiveSignals(),
                    buildSnippet(text, article.getHeadline()),
                    text != null && !text.isBlank(),
                    text == null ? 0 : text.length()
            );
        }
    }

    public record ArticleDetailResponse(
            Long id,
            String title,
            String url,
            String source,
            String host,
            NewsSourceType sourceType,
            Instant publishedAt,
            Instant discoveredAt,
            boolean candidate,
            DetectedEventType eventType,
            ReviewStatus reviewStatus,
            int candidateScore,
            CandidateStrength candidateStrength,
            String candidateReason,
            ManualReviewStatus manualReviewStatus,
            ManualReviewReason manualReviewReason,
            String manualReviewNote,
            Instant manualReviewedAt,
            boolean alertEligible,
            Instant alertQueuedAt,
            String alertReason,
            String matchedPositiveKeywords,
            String matchedNegativeKeywords,
            com.parsernews.model.ReviewVerdict reviewVerdict,
            String reviewSummary,
            List<String> reviewRiskFlags,
            List<String> reviewPositiveSignals,
            String suggestedAction,
            String targetCompany,
            String buyerCompany,
            BigDecimal offerPrice,
            String offerCurrency,
            com.parsernews.model.PaymentType paymentType,
            com.parsernews.model.DealStatus dealStatus,
            com.parsernews.model.DealConfidence dealConfidence,
            List<String> dealWarnings,
            String dealSummary,
            com.parsernews.model.DealRelevance dealRelevance,
            com.parsernews.model.Tradability tradability,
            String relevanceSummary,
            List<String> relevanceWarnings,
            List<String> relevancePositiveSignals,
            com.parsernews.model.DealStage dealStage,
            com.parsernews.model.DealTiming dealTiming,
            String stageSummary,
            List<String> stageWarnings,
            List<String> stagePositiveSignals,
            String falsePositiveReasons,
            String explanation,
            String fullText
    ) {
        static ArticleDetailResponse from(
                NewsArticleEntity article,
                DetectedEventEntity event,
                CandidateReviewInsightService reviewInsightService,
                DealTermsExtractionService dealTermsExtractionService,
                DealRelevanceService dealRelevanceService,
                DealStageDetectionService dealStageDetectionService
        ) {
            CandidateReviewInsightService.ReviewInsight insight = reviewInsightService.insight(article, event);
            DealTermsExtractionService.DealTerms dealTerms = dealTermsExtractionService.extract(article, event, insight);
            DealRelevanceService.RelevanceInsight relevance = dealRelevanceService.assess(article, event, insight, dealTerms);
            DealStageDetectionService.StageInsight stage = dealStageDetectionService.detect(article, event, dealTerms, insight, relevance);
            return new ArticleDetailResponse(
                    article.getId(),
                    article.getHeadline(),
                    article.getUrl(),
                    article.getSource().getName(),
                    extractHost(article.getUrl()),
                    article.getSource().getType(),
                    article.getPublishedAt(),
                    article.getCollectedAt(),
                    event != null,
                    event == null ? null : event.getEventType(),
                    event == null ? null : event.getReviewStatus(),
                    event == null ? 0 : event.getCandidateScore(),
                    event == null ? CandidateStrength.NONE : event.getCandidateStrength(),
                    event == null ? "No detected M&A candidate event." : event.getCandidateReason(),
                    event == null ? ManualReviewStatus.PENDING : event.getManualReviewStatus(),
                    event == null ? null : event.getManualReviewReason(),
                    event == null ? null : event.getManualReviewNote(),
                    event == null ? null : event.getManualReviewedAt(),
                    event != null && event.isAlertEligible(),
                    event == null ? null : event.getAlertQueuedAt(),
                    event == null ? null : event.getAlertReason(),
                    event == null ? null : event.getMatchedPositiveKeywords(),
                    event == null ? null : event.getMatchedNegativeKeywords(),
                    insight.reviewVerdict(),
                    insight.reviewSummary(),
                    insight.reviewRiskFlags(),
                    insight.reviewPositiveSignals(),
                    insight.suggestedAction(),
                    dealTerms.targetCompany(),
                    dealTerms.buyerCompany(),
                    dealTerms.offerPrice(),
                    dealTerms.offerCurrency(),
                    dealTerms.paymentType(),
                    dealTerms.dealStatus(),
                    dealTerms.dealConfidence(),
                    dealTerms.dealWarnings(),
                    dealTerms.dealSummary(),
                    relevance.dealRelevance(),
                    relevance.tradability(),
                    relevance.relevanceSummary(),
                    relevance.relevanceWarnings(),
                    relevance.relevancePositiveSignals(),
                    stage.dealStage(),
                    stage.dealTiming(),
                    stage.stageSummary(),
                    stage.stageWarnings(),
                    stage.stagePositiveSignals(),
                    event == null ? null : event.getFalsePositiveReasons(),
                    event == null ? null : event.getExplanation(),
                    ArticleTextCleaner.cleanTextForSnippet(article.getArticleText(), article.getHeadline())
            );
        }
    }

    private static String buildSnippet(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = ArticleTextCleaner.cleanTextForSnippet(text, fallback).trim();
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 240).trim();
    }

    private static String extractHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public record ManualReviewRequest(
            ManualReviewStatus status,
            ManualReviewReason reason,
            String note
    ) {
    }
}
