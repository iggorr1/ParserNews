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
import com.parsernews.service.AlertNotifier;
import com.parsernews.service.SignalTelegramMessageFormatter;
import com.parsernews.config.TelegramAlertSettings;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
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
    private final SignalTelegramMessageFormatter signalTelegramMessageFormatter;
    private final AlertNotifier alertNotifier;
    private final TelegramAlertSettings telegramAlertSettings;

    public SignalInboxController(
            DetectedEventRepository eventRepository,
            SecFilingRepository secFilingRepository,
            CandidateReviewInsightService reviewInsightService,
            DealTermsExtractionService dealTermsExtractionService,
            DealRelevanceService dealRelevanceService,
            DealStageDetectionService dealStageDetectionService,
            SignalTelegramMessageFormatter signalTelegramMessageFormatter,
            AlertNotifier alertNotifier,
            TelegramAlertSettings telegramAlertSettings
    ) {
        this.eventRepository = eventRepository;
        this.secFilingRepository = secFilingRepository;
        this.reviewInsightService = reviewInsightService;
        this.dealTermsExtractionService = dealTermsExtractionService;
        this.dealRelevanceService = dealRelevanceService;
        this.dealStageDetectionService = dealStageDetectionService;
        this.signalTelegramMessageFormatter = signalTelegramMessageFormatter;
        this.alertNotifier = alertNotifier;
        this.telegramAlertSettings = telegramAlertSettings;
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

    @GetMapping("/api/signals/{sourceType}/{id}")
    @Transactional(readOnly = true)
    public SignalDetailResponse signalDetails(
            @PathVariable SourceType sourceType,
            @PathVariable Long id
    ) {
        if (sourceType == SourceType.RSS_NEWS) {
            DetectedEventEntity event = eventRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RSS signal not found"));
            ArticleController.ArticleListResponse article = ArticleController.ArticleListResponse.from(
                    event.getArticle(),
                    event,
                    reviewInsightService,
                    dealTermsExtractionService,
                    dealRelevanceService,
                    dealStageDetectionService
            );
            return SignalDetailResponse.fromRss(event.getId(), article);
        }
        SecFilingEntity filing = secFilingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SEC signal not found"));
        return SignalDetailResponse.fromSec(filing);
    }

    @GetMapping("/api/signals/{sourceType}/{id}/telegram-preview")
    @Transactional(readOnly = true)
    public TelegramPreviewResponse telegramPreview(
            @PathVariable SourceType sourceType,
            @PathVariable Long id
    ) {
        TelegramSignal signal = telegramSignal(sourceType, id);
        TelegramReadiness readiness = telegramReadiness();
        return TelegramPreviewResponse.from(signal, readiness);
    }

    @PostMapping("/api/signals/{sourceType}/{id}/send-telegram")
    @Transactional(readOnly = true)
    public TelegramSendResponse sendTelegram(
            @PathVariable SourceType sourceType,
            @PathVariable Long id
    ) {
        TelegramSignal signal = telegramSignal(sourceType, id);
        TelegramReadiness readiness = telegramReadiness();
        if (!readiness.sendAllowed()) {
            return new TelegramSendResponse(
                    false,
                    readiness.telegramEnabled(),
                    readiness.telegramConfigured(),
                    readiness.reason(),
                    null
            );
        }
        AlertNotifier.AlertNotificationResult notification = alertNotifier.send(signal.messageText());
        return new TelegramSendResponse(
                notification.sent(),
                readiness.telegramEnabled(),
                readiness.telegramConfigured(),
                notification.reason(),
                notification.sent() ? null : notification.status()
        );
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

    private TelegramSignal telegramSignal(SourceType sourceType, Long id) {
        if (sourceType == SourceType.RSS_NEWS) {
            DetectedEventEntity event = eventRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RSS signal not found"));
            ArticleController.ArticleListResponse article = ArticleController.ArticleListResponse.from(
                    event.getArticle(),
                    event,
                    reviewInsightService,
                    dealTermsExtractionService,
                    dealRelevanceService,
                    dealStageDetectionService
            );
            return new TelegramSignal(
                    SourceType.RSS_NEWS,
                    event.getId(),
                    article.title(),
                    article.url(),
                    signalTelegramMessageFormatter.formatRss(article)
            );
        }
        SecFilingEntity filing = secFilingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SEC signal not found"));
        return new TelegramSignal(
                SourceType.SEC_FILING,
                filing.getId(),
                filing.getCompanyName() + " " + filing.getForm(),
                firstNonBlank(filing.getDocumentUrl(), filing.getFilingUrl()),
                signalTelegramMessageFormatter.formatSec(filing)
        );
    }

    private TelegramReadiness telegramReadiness() {
        boolean enabled = telegramAlertSettings.enabled();
        boolean configured = !isBlank(telegramAlertSettings.botToken()) && !isBlank(telegramAlertSettings.chatId());
        if (!enabled) {
            return new TelegramReadiness(false, configured, false, "Telegram is disabled; no external message will be sent.");
        }
        if (!configured) {
            return new TelegramReadiness(true, false, false, "Telegram is enabled but bot token or chat id is missing.");
        }
        return new TelegramReadiness(true, true, true, null);
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

    private String joinWarnings(List<String> first, List<String> second, List<String> third, List<String> fourth) {
        List<String> warnings = new ArrayList<>();
        if (first != null) {
            warnings.addAll(first);
        }
        if (second != null) {
            warnings.addAll(second);
        }
        if (third != null) {
            warnings.addAll(third);
        }
        if (fourth != null) {
            warnings.addAll(fourth);
        }
        return warnings.isEmpty() ? null : String.join("; ", warnings);
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    public record SignalDetailResponse(
            SourceType sourceType,
            Long id,
            String title,
            String url,
            String sourceName,
            String sourceHost,
            Instant publishedAt,
            LocalDate filingDate,
            CandidateStrength candidateStrength,
            Integer candidateScore,
            String candidateReason,
            String matchedPositiveKeywords,
            String matchedNegativeKeywords,
            com.parsernews.model.ReviewVerdict reviewVerdict,
            String reviewSummary,
            List<String> reviewRiskFlags,
            List<String> reviewPositiveSignals,
            String dealSummary,
            String targetCompany,
            String buyerCompany,
            BigDecimal offerPrice,
            String offerCurrency,
            com.parsernews.model.PaymentType paymentType,
            com.parsernews.model.DealStatus dealStatus,
            com.parsernews.model.DealConfidence dealConfidence,
            List<String> dealWarnings,
            DealRelevance dealRelevance,
            Tradability tradability,
            String relevanceSummary,
            List<String> relevanceWarnings,
            DealStage dealStage,
            DealTiming dealTiming,
            String stageSummary,
            List<String> stageWarnings,
            Boolean alertEligible,
            String alertEligibilityReason,
            ManualReviewStatus manualReviewStatus,
            ManualReviewReason manualReviewReason,
            String manualReviewNote,
            String companyName,
            String cik,
            String form,
            String accessionNumber,
            String filingUrl,
            String documentUrl,
            String documentFetchStatus,
            String documentTextSnippet,
            String documentSignalStrength,
            String documentSignalReason,
            SecSignalType secSignalType,
            SecSignalPriority secSignalPriority,
            String secSignalSummary,
            String secSignalWarnings
    ) {
        static SignalDetailResponse fromRss(Long signalId, ArticleController.ArticleListResponse article) {
            return new SignalDetailResponse(
                    SourceType.RSS_NEWS,
                    signalId,
                    article.title(),
                    article.url(),
                    article.source(),
                    article.host(),
                    article.publishedAt(),
                    null,
                    article.candidateStrength(),
                    article.candidateScore(),
                    article.candidateReason(),
                    article.matchedPositiveKeywords(),
                    article.matchedNegativeKeywords(),
                    article.reviewVerdict(),
                    article.reviewSummary(),
                    article.reviewRiskFlags(),
                    article.reviewPositiveSignals(),
                    article.dealSummary(),
                    article.targetCompany(),
                    article.buyerCompany(),
                    article.offerPrice(),
                    article.offerCurrency(),
                    article.paymentType(),
                    article.dealStatus(),
                    article.dealConfidence(),
                    article.dealWarnings(),
                    article.dealRelevance(),
                    article.tradability(),
                    article.relevanceSummary(),
                    article.relevanceWarnings(),
                    article.dealStage(),
                    article.dealTiming(),
                    article.stageSummary(),
                    article.stageWarnings(),
                    article.alertEligible(),
                    article.alertReason(),
                    article.manualReviewStatus(),
                    article.manualReviewReason(),
                    article.manualReviewNote(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        static SignalDetailResponse fromSec(SecFilingEntity filing) {
            return new SignalDetailResponse(
                    SourceType.SEC_FILING,
                    filing.getId(),
                    filing.getCompanyName() + " " + filing.getForm(),
                    firstNonBlankStatic(filing.getDocumentUrl(), filing.getFilingUrl()),
                    "SEC",
                    "sec.gov",
                    null,
                    filing.getFilingDate(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    filing.getManualReviewStatus(),
                    filing.getManualReviewReason(),
                    filing.getManualReviewNote(),
                    filing.getCompanyName(),
                    filing.getCik(),
                    filing.getForm(),
                    filing.getAccessionNumber(),
                    filing.getFilingUrl(),
                    filing.getDocumentUrl(),
                    filing.getDocumentFetchStatus(),
                    filing.getDocumentTextSnippet(),
                    filing.getDocumentSignalStrength(),
                    filing.getDocumentSignalReason(),
                    filing.getSecSignalType(),
                    filing.getSecSignalPriority(),
                    filing.getSecSignalSummary(),
                    filing.getSecSignalWarnings()
            );
        }

        private static String firstNonBlankStatic(String first, String fallback) {
            return first == null || first.isBlank() ? fallback : first;
        }
    }

    public record TelegramPreviewResponse(
            SourceType sourceType,
            Long id,
            String title,
            String url,
            String messageText,
            boolean telegramEnabled,
            boolean telegramConfigured,
            boolean sendAllowed,
            String reason
    ) {
        static TelegramPreviewResponse from(TelegramSignal signal, TelegramReadiness readiness) {
            return new TelegramPreviewResponse(
                    signal.sourceType(),
                    signal.id(),
                    signal.title(),
                    signal.url(),
                    signal.messageText(),
                    readiness.telegramEnabled(),
                    readiness.telegramConfigured(),
                    readiness.sendAllowed(),
                    readiness.reason()
            );
        }
    }

    public record TelegramSendResponse(
            boolean sent,
            boolean telegramEnabled,
            boolean telegramConfigured,
            String message,
            String error
    ) {
    }

    private record TelegramSignal(
            SourceType sourceType,
            Long id,
            String title,
            String url,
            String messageText
    ) {
    }

    private record TelegramReadiness(
            boolean telegramEnabled,
            boolean telegramConfigured,
            boolean sendAllowed,
            String reason
    ) {
    }
}
