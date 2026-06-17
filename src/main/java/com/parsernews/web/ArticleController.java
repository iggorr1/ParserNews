package com.parsernews.web;

import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsArticleRepository;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import com.parsernews.util.ArticleTextCleaner;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestController
public class ArticleController {
    private final NewsArticleRepository articleRepository;
    private final DetectedEventRepository eventRepository;

    public ArticleController(
            NewsArticleRepository articleRepository,
            DetectedEventRepository eventRepository
    ) {
        this.articleRepository = articleRepository;
        this.eventRepository = eventRepository;
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
                .map(article -> ArticleListResponse.from(article, eventRepository.findByArticle(article).orElse(null)))
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
        return ArticleDetailResponse.from(article, eventRepository.findByArticle(article).orElse(null));
    }

    private List<ArticleListResponse> candidates(int limit) {
        return eventRepository.findTop200ByOrderByDetectedAtDesc().stream()
                .filter(event -> event.getCandidateStrength() != CandidateStrength.NONE)
                .sorted(Comparator.comparingInt(DetectedEventEntity::getCandidateScore).reversed()
                        .thenComparing(DetectedEventEntity::getDetectedAt, Comparator.reverseOrder()))
                .limit(normalizedLimit(limit))
                .map(event -> ArticleListResponse.from(event.getArticle(), event))
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
            String matchedPositiveKeywords,
            String matchedNegativeKeywords,
            String snippet,
            boolean hasFullText,
            int fullTextLength
    ) {
        static ArticleListResponse from(NewsArticleEntity article, DetectedEventEntity event) {
            String text = article.getArticleText();
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
                    event == null ? null : event.getMatchedPositiveKeywords(),
                    event == null ? null : event.getMatchedNegativeKeywords(),
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
            String matchedPositiveKeywords,
            String matchedNegativeKeywords,
            String falsePositiveReasons,
            String explanation,
            String fullText
    ) {
        static ArticleDetailResponse from(NewsArticleEntity article, DetectedEventEntity event) {
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
                    event == null ? null : event.getMatchedPositiveKeywords(),
                    event == null ? null : event.getMatchedNegativeKeywords(),
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
}
