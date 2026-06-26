package com.parsernews.service;

import com.parsernews.model.ReviewVerdict;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SourceStatsService {
    private final NewsArticleRepository articleRepository;
    private final DetectedEventRepository eventRepository;
    private final CandidateReviewInsightService reviewInsightService;

    public SourceStatsService(
            NewsArticleRepository articleRepository,
            DetectedEventRepository eventRepository,
            CandidateReviewInsightService reviewInsightService
    ) {
        this.articleRepository = articleRepository;
        this.eventRepository = eventRepository;
        this.reviewInsightService = reviewInsightService;
    }

    @Transactional(readOnly = true)
    public List<SourceStatsResponse> sourceStats() {
        Map<SourceKey, MutableSourceStats> stats = new LinkedHashMap<>();
        for (NewsArticleEntity article : articleRepository.findAll()) {
            MutableSourceStats s = stats.computeIfAbsent(SourceKey.from(article), MutableSourceStats::new);
            s.totalArticles++;
            s.tier = article.getSource().getTier();
        }

        for (DetectedEventEntity event : eventRepository.findAll()) {
            NewsArticleEntity article = event.getArticle();
            MutableSourceStats sourceStats = stats.computeIfAbsent(SourceKey.from(article), MutableSourceStats::new);
            if (event.getCandidateStrength() != CandidateStrength.NONE) {
                sourceStats.totalCandidates++;
            }
            switch (event.getCandidateStrength()) {
                case HIGH -> sourceStats.highCandidates++;
                case MEDIUM -> sourceStats.mediumCandidates++;
                case LOW -> sourceStats.lowCandidates++;
                case NONE -> {
                }
            }
            switch (reviewInsightService.insight(article, event).reviewVerdict()) {
                case LIKELY_DEAL -> sourceStats.likelyDeals++;
                case NEEDS_REVIEW -> sourceStats.needsReview++;
                case LAW_FIRM_ALERT -> sourceStats.lawFirmAlerts++;
                case FINANCING_OR_OFFERING -> sourceStats.financingOrOffering++;
                case LIKELY_NOISE -> sourceStats.likelyNoise++;
                case UNKNOWN -> sourceStats.unknown++;
            }
            switch (event.getManualReviewStatus()) {
                case USEFUL -> sourceStats.manualUseful++;
                case IGNORED -> sourceStats.manualIgnored++;
                case PENDING -> sourceStats.manualPending++;
            }
        }

        return stats.values().stream()
                .map(MutableSourceStats::toResponse)
                .sorted(Comparator.comparingInt(SourceStatsResponse::likelyDeals).reversed()
                        .thenComparing(SourceStatsResponse::needsReview, Comparator.reverseOrder())
                        .thenComparing(SourceStatsResponse::totalCandidates, Comparator.reverseOrder())
                        .thenComparing(SourceStatsResponse::totalArticles, Comparator.reverseOrder())
                        .thenComparing(SourceStatsResponse::source))
                .toList();
    }

    public record SourceStatsResponse(
            String source,
            String host,
            com.parsernews.persistence.SourceTier tier,
            int totalArticles,
            int totalCandidates,
            int highCandidates,
            int mediumCandidates,
            int lowCandidates,
            int likelyDeals,
            int needsReview,
            int lawFirmAlerts,
            int financingOrOffering,
            int likelyNoise,
            int unknown,
            int manualUseful,
            int manualIgnored,
            int manualPending
    ) {
    }

    private record SourceKey(String source, String host) {
        static SourceKey from(NewsArticleEntity article) {
            return new SourceKey(article.getSource().getName(), host(article.getUrl()));
        }

        private static String host(String url) {
            try {
                return URI.create(url).getHost();
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }

    private static final class MutableSourceStats {
        private final SourceKey sourceKey;
        private com.parsernews.persistence.SourceTier tier = com.parsernews.persistence.SourceTier.BROAD;
        private int totalArticles;
        private int totalCandidates;
        private int highCandidates;
        private int mediumCandidates;
        private int lowCandidates;
        private int likelyDeals;
        private int needsReview;
        private int lawFirmAlerts;
        private int financingOrOffering;
        private int likelyNoise;
        private int unknown;
        private int manualUseful;
        private int manualIgnored;
        private int manualPending;

        private MutableSourceStats(SourceKey sourceKey) {
            this.sourceKey = sourceKey;
        }

        private SourceStatsResponse toResponse() {
            return new SourceStatsResponse(
                    sourceKey.source(),
                    sourceKey.host(),
                    tier,
                    totalArticles,
                    totalCandidates,
                    highCandidates,
                    mediumCandidates,
                    lowCandidates,
                    likelyDeals,
                    needsReview,
                    lawFirmAlerts,
                    financingOrOffering,
                    likelyNoise,
                    unknown,
                    manualUseful,
                    manualIgnored,
                    manualPending
            );
        }
    }
}
