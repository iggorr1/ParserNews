package com.parsernews.service;

import com.parsernews.config.OpenAiAnalysisSettings;
import com.parsernews.persistence.AiReviewConfidence;
import com.parsernews.persistence.AiReviewVerdict;
import com.parsernews.persistence.DealGroupAiReviewEntity;
import com.parsernews.persistence.DealGroupAiReviewRepository;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.web.SignalInboxController.SourceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DealGroupAiReviewService {
    private static final String DISABLED_MESSAGE = "OpenAI AI Review is disabled. Enable OPENAI_ANALYSIS_ENABLED=true to use it.";
    private static final String MISSING_KEY_MESSAGE = "OpenAI AI Review is enabled but OPENAI_API_KEY is missing.";

    private final OpenAiAnalysisSettings settings;
    private final OpenAiAnalysisClient openAiAnalysisClient;
    private final DealGroupingService dealGroupingService;
    private final DealGroupAiReviewRepository repository;

    public DealGroupAiReviewService(
            OpenAiAnalysisSettings settings,
            OpenAiAnalysisClient openAiAnalysisClient,
            DealGroupingService dealGroupingService,
            DealGroupAiReviewRepository repository
    ) {
        this.settings = settings;
        this.openAiAnalysisClient = openAiAnalysisClient;
        this.dealGroupingService = dealGroupingService;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AiReviewResponse latest(String groupKey) {
        return repository.findTopByGroupKeyOrderByCreatedAtDesc(groupKey)
                .map(entity -> response(true, configured(), "Latest AI review loaded.", entity))
                .orElseGet(() -> AiReviewResponse.empty(settings.enabled(), configured(), "No AI review has been saved for this deal group yet."));
    }

    @Transactional
    public AiReviewResponse review(String groupKey) {
        if (!settings.enabled()) {
            return AiReviewResponse.empty(false, configured(), DISABLED_MESSAGE);
        }
        if (!configured()) {
            return AiReviewResponse.empty(true, false, MISSING_KEY_MESSAGE);
        }
        DealGroupingService.DealGroupResponse group = dealGroupingService.group(groupKey)
                .orElseThrow(() -> new IllegalArgumentException("Deal group not found: " + groupKey));
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
        return response(true, true, "AI review completed and saved.", saved);
    }

    private boolean configured() {
        return settings.apiKey() != null && !settings.apiKey().isBlank();
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
}
