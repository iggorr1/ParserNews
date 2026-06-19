package com.parsernews.web;

import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealStage;
import com.parsernews.model.DealTiming;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.persistence.SecSignalPriority;
import com.parsernews.persistence.SecSignalType;
import com.parsernews.service.CandidateReviewInsightService;
import com.parsernews.service.DealRelevanceService;
import com.parsernews.service.DealStageDetectionService;
import com.parsernews.service.DealTermsExtractionService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
public class SignalInboxController {
    private final DetectedEventRepository eventRepository;
    private final SecFilingRepository secFilingRepository;
    private final CandidateReviewInsightService reviewInsightService;
    private final DealTermsExtractionService dealTermsExtractionService;
    private final DealRelevanceService dealRelevanceService;
    private final DealStageDetectionService dealStageDetectionService;

    public SignalInboxController(
            DetectedEventRepository eventRepository,
            SecFilingRepository secFilingRepository,
            CandidateReviewInsightService reviewInsightService,
            DealTermsExtractionService dealTermsExtractionService,
            DealRelevanceService dealRelevanceService,
            DealStageDetectionService dealStageDetectionService
    ) {
        this.eventRepository = eventRepository;
        this.secFilingRepository = secFilingRepository;
        this.reviewInsightService = reviewInsightService;
        this.dealTermsExtractionService = dealTermsExtractionService;
        this.dealRelevanceService = dealRelevanceService;
        this.dealStageDetectionService = dealStageDetectionService;
    }

    @GetMapping("/api/signals")
    @Transactional(readOnly = true)
    public List<UnifiedSignalResponse> signals(
            @RequestParam(required = false) SourceType sourceType,
            @RequestParam(required = false) UnifiedPriority priority,
            @RequestParam(required = false) ManualReviewStatus reviewStatus,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<UnifiedSignalResponse> signals = new ArrayList<>();
        if (sourceType == null || sourceType == SourceType.RSS_NEWS) {
            signals.addAll(eventRepository.findTop200ByOrderByDetectedAtDesc().stream()
                    .filter(event -> event.getCandidateStrength() != CandidateStrength.NONE)
                    .map(this::fromRssEvent)
                    .toList());
        }
        if (sourceType == null || sourceType == SourceType.SEC_FILING) {
            signals.addAll(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc().stream()
                    .map(this::fromSecFiling)
                    .toList());
        }

        return signals.stream()
                .filter(signal -> priority == null || signal.priority() == priority)
                .filter(signal -> reviewStatus == null
                        ? signal.reviewStatus() != ManualReviewStatus.IGNORED
                        : signal.reviewStatus() == reviewStatus)
                .sorted(signalComparator())
                .limit(normalizedLimit(limit))
                .toList();
    }

    private UnifiedSignalResponse fromRssEvent(DetectedEventEntity event) {
        ArticleController.ArticleListResponse article = ArticleController.ArticleListResponse.from(
                event.getArticle(),
                event,
                reviewInsightService,
                dealTermsExtractionService,
                dealRelevanceService,
                dealStageDetectionService
        );
        return new UnifiedSignalResponse(
                event.getId(),
                SourceType.RSS_NEWS,
                article.title(),
                article.url(),
                article.source(),
                article.host(),
                null,
                article.publishedAt(),
                null,
                article.discoveredAt(),
                priorityFromCandidateStrength(article.candidateStrength()),
                article.eventType() == null ? "UNKNOWN" : article.eventType().name(),
                article.reviewSummary(),
                joinWarnings(article.reviewRiskFlags(), article.dealWarnings(), article.relevanceWarnings(), article.stageWarnings()),
                article.manualReviewStatus(),
                article.manualReviewReason(),
                article.manualReviewNote(),
                article.manualReviewedAt(),
                article.alertEligible(),
                article.dealRelevance(),
                article.tradability(),
                article.dealStage(),
                article.dealTiming(),
                null,
                null,
                article.id()
        );
    }

    private UnifiedSignalResponse fromSecFiling(SecFilingEntity filing) {
        return new UnifiedSignalResponse(
                filing.getId(),
                SourceType.SEC_FILING,
                filing.getCompanyName() + " " + filing.getForm(),
                filing.getFilingUrl(),
                "SEC",
                "sec.gov",
                filing.getCompanyName(),
                null,
                filing.getFilingDate(),
                filing.getProcessedAt(),
                priorityFromSecPriority(filing.getSecSignalPriority()),
                filing.getSecSignalType().name(),
                firstNonBlank(filing.getSecSignalSummary(), filing.getSignalReason()),
                filing.getSecSignalWarnings(),
                filing.getManualReviewStatus(),
                filing.getManualReviewReason(),
                filing.getManualReviewNote(),
                filing.getManualReviewedAt(),
                null,
                null,
                null,
                null,
                null,
                filing.getSecSignalType(),
                filing.getSecSignalPriority(),
                null
        );
    }

    private Comparator<UnifiedSignalResponse> signalComparator() {
        return Comparator
                .comparingInt((UnifiedSignalResponse signal) -> priorityRank(signal.priority())).reversed()
                .thenComparing(signal -> Boolean.TRUE.equals(signal.alertEligible()), Comparator.reverseOrder())
                .thenComparing(signal -> signal.sourceType() == SourceType.SEC_FILING && signal.priority() == UnifiedPriority.HIGH, Comparator.reverseOrder())
                .thenComparing(UnifiedSignalResponse::sortInstant, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(signal -> signal.reviewStatus() == ManualReviewStatus.IGNORED);
    }

    private UnifiedPriority priorityFromCandidateStrength(CandidateStrength strength) {
        return switch (strength == null ? CandidateStrength.NONE : strength) {
            case HIGH -> UnifiedPriority.HIGH;
            case MEDIUM -> UnifiedPriority.MEDIUM;
            case LOW -> UnifiedPriority.LOW;
            case NONE -> UnifiedPriority.NONE;
        };
    }

    private UnifiedPriority priorityFromSecPriority(SecSignalPriority priority) {
        return switch (priority == null ? SecSignalPriority.UNKNOWN : priority) {
            case HIGH -> UnifiedPriority.HIGH;
            case MEDIUM -> UnifiedPriority.MEDIUM;
            case LOW -> UnifiedPriority.LOW;
            case NONE -> UnifiedPriority.NONE;
            case UNKNOWN -> UnifiedPriority.NONE;
        };
    }

    private int priorityRank(UnifiedPriority priority) {
        return switch (priority == null ? UnifiedPriority.NONE : priority) {
            case HIGH -> 4;
            case MEDIUM -> 3;
            case LOW -> 2;
            case NONE -> 1;
        };
    }

    private String joinWarnings(List<String>... warningLists) {
        List<String> warnings = new ArrayList<>();
        for (List<String> warningList : warningLists) {
            if (warningList != null) {
                warnings.addAll(warningList);
            }
        }
        return warnings.isEmpty() ? null : String.join("; ", warnings);
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private int normalizedLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 200);
    }

    public enum SourceType {
        RSS_NEWS,
        SEC_FILING
    }

    public enum UnifiedPriority {
        HIGH,
        MEDIUM,
        LOW,
        NONE
    }

    public record UnifiedSignalResponse(
            Long id,
            SourceType sourceType,
            String title,
            String url,
            String source,
            String host,
            String company,
            Instant publishedAt,
            LocalDate filingDate,
            Instant discoveredAt,
            UnifiedPriority priority,
            String signalType,
            String summary,
            String warnings,
            ManualReviewStatus reviewStatus,
            ManualReviewReason reviewReason,
            String reviewNote,
            Instant reviewedAt,
            Boolean alertEligible,
            DealRelevance dealRelevance,
            Tradability tradability,
            DealStage dealStage,
            DealTiming dealTiming,
            SecSignalType secSignalType,
            SecSignalPriority secSignalPriority,
            Long articleId
    ) {
        Instant sortInstant() {
            return publishedAt != null ? publishedAt : discoveredAt;
        }
    }
}
