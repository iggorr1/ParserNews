package com.parsernews.service;

import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealStage;
import com.parsernews.model.DealTiming;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.AiReviewConfidence;
import com.parsernews.persistence.AiReviewVerdict;
import com.parsernews.persistence.DealGroupAiReviewEntity;
import com.parsernews.persistence.DealGroupAiReviewRepository;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.web.SignalInboxController.SourceType;
import com.parsernews.web.SignalInboxController.UnifiedPriority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DealGroupAiReviewService {
    private static final String DISABLED_MESSAGE = "OpenAI AI Review is disabled. Enable OPENAI_ANALYSIS_ENABLED=true to use it.";
    private static final String MISSING_KEY_MESSAGE = "OpenAI AI Review is enabled but OPENAI_API_KEY is missing.";

    private final OpenAiRuntimeSettingsService settingsService;
    private final OpenAiAnalysisClient openAiAnalysisClient;
    private final DealGroupingService dealGroupingService;
    private final DealGroupAiReviewRepository repository;

    public DealGroupAiReviewService(
            OpenAiRuntimeSettingsService settingsService,
            OpenAiAnalysisClient openAiAnalysisClient,
            DealGroupingService dealGroupingService,
            DealGroupAiReviewRepository repository
    ) {
        this.settingsService = settingsService;
        this.openAiAnalysisClient = openAiAnalysisClient;
        this.dealGroupingService = dealGroupingService;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AiReviewResponse latest(String groupKey) {
        OpenAiRuntimeSettingsService.EffectiveOpenAiSettings settings = settingsService.effectiveSettings();
        return repository.findTopByGroupKeyOrderByCreatedAtDesc(groupKey)
                .map(entity -> response(settings.enabled(), settings.configured(), "Latest AI review loaded.", entity))
                .orElseGet(() -> AiReviewResponse.empty(settings.enabled(), settings.configured(), "No AI review has been saved for this deal group yet."));
    }

    @Transactional
    public AiReviewResponse review(String groupKey) {
        OpenAiRuntimeSettingsService.EffectiveOpenAiSettings settings = settingsService.effectiveSettings();
        if (!settings.enabled()) {
            return AiReviewResponse.empty(false, settings.configured(), DISABLED_MESSAGE);
        }
        if (!settings.configured()) {
            return AiReviewResponse.empty(true, false, MISSING_KEY_MESSAGE);
        }
        DealGroupingService.DealGroupResponse group = dealGroupingService.group(groupKey)
                .orElseThrow(() -> new IllegalArgumentException("Deal group not found: " + groupKey));
        DealGroupAiReviewEntity saved = saveReview(group, settings);
        return response(true, true, "AI review completed and saved.", saved);
    }

    @Transactional
    public BatchAiReviewResponse batchReview(BatchAiReviewRequest request) {
        OpenAiRuntimeSettingsService.EffectiveOpenAiSettings settings = settingsService.effectiveSettings();
        int requestedLimit = normalizedBatchLimit(request == null ? null : request.limit());
        boolean skipAlreadyReviewed = request == null || request.skipAlreadyReviewed() == null || request.skipAlreadyReviewed();
        boolean onlyPromising = request == null || request.onlyPromising() == null || request.onlyPromising();
        UnifiedPriority minPriority = request == null || request.minPriority() == null ? UnifiedPriority.HIGH : request.minPriority();
        List<DealGroupingService.DealGroupResponse> allGroups = dealGroupingService.groups(null, null, 200);
        List<DealGroupingService.DealGroupResponse> eligibleGroups = allGroups.stream()
                .filter(group -> eligibleForBatch(group, skipAlreadyReviewed, onlyPromising, minPriority))
                .limit(requestedLimit)
                .toList();
        int skippedByFilter = Math.max(0, allGroups.size() - eligibleGroups.size());

        if (!settings.enabled()) {
            return new BatchAiReviewResponse(
                    false,
                    settings.configured(),
                    DISABLED_MESSAGE,
                    requestedLimit,
                    0,
                    allGroups.size(),
                    0,
                    List.of()
            );
        }
        if (!settings.configured()) {
            return new BatchAiReviewResponse(
                    true,
                    false,
                    MISSING_KEY_MESSAGE,
                    requestedLimit,
                    0,
                    allGroups.size(),
                    0,
                    List.of()
            );
        }

        List<BatchAiReviewResult> results = new ArrayList<>();
        int failedCount = 0;
        for (DealGroupingService.DealGroupResponse group : eligibleGroups) {
            try {
                DealGroupAiReviewEntity saved = saveReview(group, settings);
                results.add(new BatchAiReviewResult(
                        group.groupKey(),
                        group.title(),
                        saved.getVerdict(),
                        saved.getConfidence(),
                        saved.getReason(),
                        null
                ));
            } catch (RuntimeException exception) {
                failedCount++;
                results.add(new BatchAiReviewResult(
                        group.groupKey(),
                        group.title(),
                        null,
                        null,
                        null,
                        exception.getMessage()
                ));
            }
        }
        return new BatchAiReviewResponse(
                true,
                true,
                "Batch AI review finished.",
                requestedLimit,
                results.size() - failedCount,
                skippedByFilter,
                failedCount,
                results
        );
    }

    @Transactional(readOnly = true)
    public AiReviewSummaryResponse summary() {
        List<DealGroupAiReviewEntity> reviews = repository.findAll();
        Map<String, DealGroupingService.DealGroupResponse> groupsByKey = dealGroupingService.groups(null, null, 200).stream()
                .collect(Collectors.toMap(
                        DealGroupingService.DealGroupResponse::groupKey,
                        Function.identity(),
                        (first, second) -> first
                ));
        List<LatestAiReviewSummary> latestReviews = repository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(review -> new LatestAiReviewSummary(
                        review.getGroupKey(),
                        Optional.ofNullable(groupsByKey.get(review.getGroupKey()))
                                .map(DealGroupingService.DealGroupResponse::title)
                                .orElse(review.getGroupKey()),
                        review.getVerdict(),
                        review.getConfidence(),
                        review.getCreatedAt(),
                        review.getReason()
                ))
                .toList();
        return new AiReviewSummaryResponse(
                reviews.size(),
                countVerdict(reviews, AiReviewVerdict.GOOD_SIGNAL),
                countVerdict(reviews, AiReviewVerdict.NOT_TRADABLE),
                countVerdict(reviews, AiReviewVerdict.PRIVATE_COMPANY),
                countVerdict(reviews, AiReviewVerdict.FALSE_POSITIVE),
                countVerdict(reviews, AiReviewVerdict.NEEDS_HUMAN_REVIEW),
                countVerdict(reviews, AiReviewVerdict.UNKNOWN),
                countConfidence(reviews, AiReviewConfidence.HIGH),
                countConfidence(reviews, AiReviewConfidence.MEDIUM),
                countConfidence(reviews, AiReviewConfidence.LOW),
                latestReviews
        );
    }

    private DealGroupAiReviewEntity saveReview(
            DealGroupingService.DealGroupResponse group,
            OpenAiRuntimeSettingsService.EffectiveOpenAiSettings settings
    ) {
        OpenAiAnalysisClient.AiReviewResult result = openAiAnalysisClient.reviewDealGroup(
                settings.model(),
                settings.apiKey(),
                prompt(),
                truncate(evidence(group), settings.maxInputChars())
        );
        DealGroupAiReviewEntity saved = repository.save(new DealGroupAiReviewEntity(
                group.groupKey(),
                settings.model(),
                nullSafe(result.verdict(), AiReviewVerdict.UNKNOWN),
                nullSafe(result.confidence(), AiReviewConfidence.LOW),
                result.tradablePublicTarget(),
                nullSafe(result.suggestedReviewStatus(), ManualReviewStatus.PENDING),
                nullSafe(result.suggestedReviewReason(), ManualReviewReason.OTHER),
                result.reason(),
                String.join("; ", result.riskFlags() == null ? List.of() : result.riskFlags()),
                result.rawJson()
        ));
        return saved;
    }

    private boolean eligibleForBatch(
            DealGroupingService.DealGroupResponse group,
            boolean skipAlreadyReviewed,
            boolean onlyPromising,
            UnifiedPriority minPriority
    ) {
        if (group == null) {
            return false;
        }
        if (skipAlreadyReviewed && repository.existsByGroupKey(group.groupKey())) {
            return false;
        }
        if (!priorityAllowed(group.priority(), minPriority)) {
            return false;
        }
        if (!onlyPromising) {
            return true;
        }
        return (group.reviewStatus() == ManualReviewStatus.PENDING || group.reviewStatus() == ManualReviewStatus.USEFUL)
                && (group.dealTiming() == DealTiming.EARLY || group.dealTiming() == DealTiming.MID_STAGE || group.dealTiming() == DealTiming.UNKNOWN)
                && (group.tradability() == Tradability.HIGH || group.tradability() == Tradability.MEDIUM)
                && (group.dealRelevance() == DealRelevance.PUBLIC_CASH_ACQUISITION
                || group.dealRelevance() == DealRelevance.PUBLIC_TAKE_PRIVATE
                || group.dealRelevance() == DealRelevance.PUBLIC_PUBLIC_MERGER
                || group.dealRelevance() == DealRelevance.UNKNOWN)
                && group.dealRelevance() != DealRelevance.LAW_FIRM_OR_SHAREHOLDER_ALERT
                && group.dealRelevance() != DealRelevance.PRIVATE_COMPANY_ACQUISITION
                && group.dealRelevance() != DealRelevance.REVERSE_TAKEOVER
                && group.dealRelevance() != DealRelevance.NOT_TRADABLE
                && group.dealStage() != DealStage.LITIGATION_OR_LAW_FIRM_UPDATE
                && group.reviewReason() != ManualReviewReason.FALSE_POSITIVE
                && group.reviewReason() != ManualReviewReason.PRIVATE_COMPANY
                && group.reviewReason() != ManualReviewReason.NOT_TRADABLE
                && group.reviewReason() != ManualReviewReason.LATE_STAGE_UPDATE
                && group.reviewReason() != ManualReviewReason.LAW_FIRM_OR_SHAREHOLDER_ALERT
                && warningsAllowBatch(group.warnings());
    }

    private boolean priorityAllowed(UnifiedPriority priority, UnifiedPriority minPriority) {
        if (priority == null || minPriority == null) {
            return false;
        }
        if (minPriority == UnifiedPriority.HIGH) {
            return priority == UnifiedPriority.HIGH;
        }
        if (minPriority == UnifiedPriority.MEDIUM) {
            return priority == UnifiedPriority.HIGH || priority == UnifiedPriority.MEDIUM;
        }
        return true;
    }

    private boolean warningsAllowBatch(List<String> warnings) {
        String joined = String.join(" ", warnings == null ? List.of() : warnings).toLowerCase();
        return !joined.contains("law firm")
                && !joined.contains("shareholder alert")
                && !joined.contains("private company")
                && !joined.contains("reverse takeover")
                && !joined.contains("not tradable")
                && !joined.contains("noise");
    }

    private int normalizedBatchLimit(Integer limit) {
        if (limit == null) {
            return 10;
        }
        return Math.max(1, Math.min(limit, 25));
    }

    private long countVerdict(List<DealGroupAiReviewEntity> reviews, AiReviewVerdict verdict) {
        return reviews.stream().filter(review -> review.getVerdict() == verdict).count();
    }

    private long countConfidence(List<DealGroupAiReviewEntity> reviews, AiReviewConfidence confidence) {
        return reviews.stream().filter(review -> review.getConfidence() == confidence).count();
    }

    private String prompt() {
        return """
                You are reviewing one grouped M&A research signal.
                Judge only whether this Deal Group is a useful M&A research signal for human review:
                public target, tradable target, cash or fixed-price offer, and early enough signal.
                Do not give buy/sell/investment advice. Do not recommend trading.
                Return only the requested structured JSON.
                """;
    }

    private String evidence(DealGroupingService.DealGroupResponse group) {
        StringBuilder builder = new StringBuilder();
        builder.append("Deal Group Key: ").append(group.groupKey()).append('\n');
        builder.append("Title: ").append(group.title()).append('\n');
        builder.append("Buyer: ").append(group.buyerCompany()).append(" ticker=").append(group.buyerTicker())
                .append(" cik=").append(group.buyerCik()).append('\n');
        builder.append("Target: ").append(group.targetCompany()).append(" ticker=").append(group.targetTicker())
                .append(" cik=").append(group.targetCik()).append('\n');
        builder.append("Priority: ").append(group.priority()).append('\n');
        builder.append("Relevance: ").append(group.dealRelevance()).append('\n');
        builder.append("Tradability: ").append(group.tradability()).append('\n');
        builder.append("Stage/Timing: ").append(group.dealStage()).append(" / ").append(group.dealTiming()).append('\n');
        builder.append("Manual Review: ").append(group.reviewStatus()).append(" / ").append(group.reviewReason()).append('\n');
        builder.append("Warnings: ").append(String.join("; ", group.warnings())).append('\n');
        builder.append("Evidence URLs: ").append(String.join("; ", group.evidenceUrls())).append('\n');
        builder.append("Related Signals:\n");
        for (DealGroupingService.RelatedSignalResponse signal : group.relatedSignals()) {
            builder.append("- ").append(signal.sourceType()).append(" #").append(signal.id())
                    .append(" priority=").append(signal.priority())
                    .append(" type=").append(signal.signalType())
                    .append(" date=").append(signal.date() == null ? signal.filingDate() : signal.date())
                    .append('\n')
                    .append("  title=").append(signal.title()).append('\n')
                    .append("  url=").append(signal.url()).append('\n')
                    .append("  relatedReason=").append(signal.relatedReason()).append('\n');
            if (signal.sourceType() == SourceType.SEC_FILING) {
                builder.append("  note=SEC filing evidence; verify if this is early deal evidence or later filing update.\n");
            }
        }
        return builder.toString();
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n[truncated]";
    }

    private <T> T nullSafe(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private AiReviewResponse response(boolean enabled, boolean configured, String message, DealGroupAiReviewEntity entity) {
        return new AiReviewResponse(
                enabled,
                configured,
                message,
                entity.getId(),
                entity.getGroupKey(),
                entity.getModel(),
                entity.getVerdict(),
                entity.getConfidence(),
                Boolean.TRUE.equals(entity.getTradablePublicTarget()),
                entity.getSuggestedReviewStatus(),
                entity.getSuggestedReviewReason(),
                entity.getReason(),
                riskFlags(entity.getRiskFlags()),
                entity.getCreatedAt()
        );
    }

    private List<String> riskFlags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(";"))
                .map(String::trim)
                .filter(flag -> !flag.isBlank())
                .toList();
    }

    public record AiReviewResponse(
            boolean openAiEnabled,
            boolean openAiConfigured,
            String message,
            Long id,
            String groupKey,
            String model,
            AiReviewVerdict verdict,
            AiReviewConfidence confidence,
            boolean tradablePublicTarget,
            ManualReviewStatus suggestedReviewStatus,
            ManualReviewReason suggestedReviewReason,
            String reason,
            List<String> riskFlags,
            Instant createdAt
    ) {
        public static AiReviewResponse empty(boolean enabled, boolean configured, String message) {
            return new AiReviewResponse(
                    enabled,
                    configured,
                    message,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    List.of(),
                    null
            );
        }
    }

    public record BatchAiReviewRequest(
            Integer limit,
            Boolean skipAlreadyReviewed,
            UnifiedPriority minPriority,
            Boolean onlyPromising
    ) {
    }

    public record BatchAiReviewResponse(
            boolean enabled,
            boolean configured,
            String message,
            int requestedLimit,
            int reviewedCount,
            int skippedCount,
            int failedCount,
            List<BatchAiReviewResult> results
    ) {
    }

    public record BatchAiReviewResult(
            String groupKey,
            String title,
            AiReviewVerdict verdict,
            AiReviewConfidence confidence,
            String reason,
            String error
    ) {
    }

    public record AiReviewSummaryResponse(
            long totalAiReviewed,
            long goodSignalCount,
            long notTradableCount,
            long privateCompanyCount,
            long falsePositiveCount,
            long needsHumanReviewCount,
            long unknownCount,
            long highConfidenceCount,
            long mediumConfidenceCount,
            long lowConfidenceCount,
            List<LatestAiReviewSummary> latestReviews
    ) {
    }

    public record LatestAiReviewSummary(
            String groupKey,
            String title,
            AiReviewVerdict verdict,
            AiReviewConfidence confidence,
            Instant createdAt,
            String reason
    ) {
    }
}
