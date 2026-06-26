package com.parsernews.service;

import com.parsernews.persistence.ScanRunEntity;
import com.parsernews.persistence.ScanRunRepository;
import com.parsernews.web.DealGroupController;
import com.parsernews.web.SignalInboxController;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewPacketService {
    private final StatusService statusService;
    private final SchedulerStatusService schedulerStatusService;
    private final OpenAiRuntimeSettingsService openAiRuntimeSettingsService;
    private final TelegramRuntimeSettingsService telegramRuntimeSettingsService;
    private final DealGroupingService dealGroupingService;
    private final DealGroupAiReviewService dealGroupAiReviewService;
    private final SourceEvaluationPreviewService sourceEvaluationPreviewService;
    private final SecDiscoveryScanner secDiscoveryScanner;
    private final ScanRunRepository scanRunRepository;

    public ReviewPacketService(
            StatusService statusService,
            SchedulerStatusService schedulerStatusService,
            OpenAiRuntimeSettingsService openAiRuntimeSettingsService,
            TelegramRuntimeSettingsService telegramRuntimeSettingsService,
            DealGroupingService dealGroupingService,
            DealGroupAiReviewService dealGroupAiReviewService,
            SourceEvaluationPreviewService sourceEvaluationPreviewService,
            SecDiscoveryScanner secDiscoveryScanner,
            ScanRunRepository scanRunRepository
    ) {
        this.statusService = statusService;
        this.schedulerStatusService = schedulerStatusService;
        this.openAiRuntimeSettingsService = openAiRuntimeSettingsService;
        this.telegramRuntimeSettingsService = telegramRuntimeSettingsService;
        this.dealGroupingService = dealGroupingService;
        this.dealGroupAiReviewService = dealGroupAiReviewService;
        this.sourceEvaluationPreviewService = sourceEvaluationPreviewService;
        this.secDiscoveryScanner = secDiscoveryScanner;
        this.scanRunRepository = scanRunRepository;
    }

    @Transactional(readOnly = true)
    public ReviewPacket packet() {
        StatusService.StatusResponse appStatus = statusService.status();
        SchedulerStatusService.SchedulerStatusResponse schedulerStatus = schedulerStatusService.status();
        OpenAiRuntimeSettingsService.EffectiveOpenAiSettings openAiStatus = openAiRuntimeSettingsService.effectiveSettings();
        TelegramRuntimeSettingsService.EffectiveTelegramSettings telegramStatus = telegramRuntimeSettingsService.effectiveSettings();
        List<DealGroupingService.DealGroupResponse> topDealGroups = dealGroupingService.groups(null, null, 30);
        DealGroupController.DealGroupStatsResponse dealGroupStats = dealGroupStats();
        DealGroupAiReviewService.AiReviewSummaryResponse aiSummary = dealGroupAiReviewService.summary();
        SourceQualityAudit sourceQualityAudit = sourceQualityAudit();
        SecDiscoveryScanner.SecDiscoveryStatus secDiscoveryStatus = secDiscoveryScanner.status();
        DealGroupAiReviewService.BatchCandidatePreviewResponse batchPreview = dealGroupAiReviewService.previewBatchCandidates(
                new DealGroupAiReviewService.BatchCandidatePreviewRequest(
                        25,
                        true,
                        SignalInboxController.UnifiedPriority.MEDIUM,
                        true
                )
        );
        List<ScanRunSummary> recentScanRuns = scanRunRepository.findTop100ByOrderByStartedAtDesc().stream()
                .limit(10)
                .map(ScanRunSummary::from)
                .toList();
        List<String> knownWarnings = knownWarnings(appStatus, schedulerStatus, openAiStatus, batchPreview, secDiscoveryStatus);
        return new ReviewPacket(
                Instant.now(),
                appStatus,
                schedulerStatus,
                secDiscoveryStatus,
                safeOpenAiStatus(openAiStatus),
                safeTelegramStatus(telegramStatus, appStatus.config().alertDispatchEnabled()),
                dealGroupStats,
                aiSummary,
                sourceQualityAudit,
                batchPreview,
                topDealGroups,
                recentScanRuns,
                knownWarnings
        );
    }

    @Transactional(readOnly = true)
    public String markdown() {
        return toMarkdown(packet());
    }

    private DealGroupController.DealGroupStatsResponse dealGroupStats() {
        List<DealGroupingService.DealGroupResponse> groups = dealGroupingService.groups(null, null, 200);
        long groupedEvidenceTotal = groups.stream()
                .mapToLong(group -> group.relatedSignals().size())
                .sum();
        double averageEvidencePerGroup = groups.isEmpty()
                ? 0.0
                : (double) groupedEvidenceTotal / groups.size();
        return new DealGroupController.DealGroupStatsResponse(
                groups.size(),
                groups.stream().filter(group -> group.reviewStatus() == com.parsernews.persistence.ManualReviewStatus.PENDING).count(),
                groups.stream().filter(group -> group.reviewStatus() == com.parsernews.persistence.ManualReviewStatus.USEFUL).count(),
                groups.stream().filter(group -> group.reviewStatus() == com.parsernews.persistence.ManualReviewStatus.IGNORED).count(),
                groups.stream().filter(group -> group.priority() == SignalInboxController.UnifiedPriority.HIGH).count(),
                groups.stream().filter(this::isAlertLikeGroup).count(),
                groupedEvidenceTotal,
                averageEvidencePerGroup,
                reviewReasonBreakdown(groups),
                enumBreakdown(groups.stream().map(group -> group.dealRelevance() == null ? null : group.dealRelevance().name()).toList()),
                enumBreakdown(groups.stream().map(group -> group.tradability() == null ? null : group.tradability().name()).toList()),
                enumBreakdown(groups.stream().map(group -> group.dealStage() == null ? null : group.dealStage().name()).toList()),
                enumBreakdown(groups.stream().map(group -> group.dealTiming() == null ? null : group.dealTiming().name()).toList()),
                enumBreakdown(groups.stream().map(group -> group.priority() == null ? null : group.priority().name()).toList())
        );
    }

    private SourceQualityAudit sourceQualityAudit() {
        try {
            SourceEvaluationPreviewService.ConfiguredSourceEvaluationResponse response =
                    sourceEvaluationPreviewService.previewConfigured(
                            new SourceEvaluationPreviewService.ConfiguredSourceEvaluationRequest(20)
                    );
            List<SourceEvaluationPreviewService.SourceEvaluationSummary> sources = response.results();
            return new SourceQualityAudit(
                    response.sourceCount(),
                    sources.stream().filter(source -> source.recommendation() == SourceEvaluationPreviewService.Recommendation.KEEP).count(),
                    sources.stream().filter(source -> source.recommendation() == SourceEvaluationPreviewService.Recommendation.NEEDS_REVIEW).count(),
                    sources.stream().filter(source -> source.recommendation() == SourceEvaluationPreviewService.Recommendation.DISABLE).count(),
                    sources.stream().mapToLong(SourceEvaluationPreviewService.SourceEvaluationSummary::strictCandidateCount).sum(),
                    sources
            );
        } catch (RuntimeException exception) {
            return new SourceQualityAudit(
                    0,
                    0,
                    1,
                    0,
                    0,
                    List.of(new SourceEvaluationPreviewService.SourceEvaluationSummary(
                            "Source Quality Audit",
                            "-",
                            0,
                            0,
                            0,
                            0,
                            SourceEvaluationPreviewService.Recommendation.NEEDS_REVIEW,
                            List.of("Source audit failed: " + exception.getMessage())
                    ))
            );
        }
    }

    private boolean isAlertLikeGroup(DealGroupingService.DealGroupResponse group) {
        return group.tradability() != com.parsernews.model.Tradability.NOT_TRADABLE
                && group.dealRelevance() != com.parsernews.model.DealRelevance.NOT_TRADABLE
                && group.warnings().stream()
                .anyMatch(warning -> "RSS signal is alert eligible".equalsIgnoreCase(warning));
    }

    private Map<String, Long> reviewReasonBreakdown(List<DealGroupingService.DealGroupResponse> groups) {
        return enumBreakdown(groups.stream()
                .map(group -> group.reviewReason() == null ? null : group.reviewReason().name())
                .toList());
    }

    private Map<String, Long> enumBreakdown(List<String> values) {
        Map<String, Long> counts = new LinkedHashMap<>();
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> counts.compute(value, (key, count) -> count == null ? 1 : count + 1));
        return counts;
    }

    private SafeOpenAiStatus safeOpenAiStatus(OpenAiRuntimeSettingsService.EffectiveOpenAiSettings settings) {
        return new SafeOpenAiStatus(
                settings.enabled(),
                settings.configured(),
                settings.keySource(),
                settings.keyMasked(),
                settings.model(),
                settings.maxInputChars(),
                settings.message()
        );
    }

    private TelegramStatus safeTelegramStatus(TelegramRuntimeSettingsService.EffectiveTelegramSettings settings, boolean dispatchEnabled) {
        return new TelegramStatus(
                settings.enabled(),
                settings.configured(),
                settings.tokenSource(),
                settings.chatIdSource(),
                settings.tokenMasked(),
                settings.chatIdMasked(),
                dispatchEnabled,
                settings.message()
        );
    }

    private List<String> knownWarnings(
            StatusService.StatusResponse appStatus,
            SchedulerStatusService.SchedulerStatusResponse schedulerStatus,
            OpenAiRuntimeSettingsService.EffectiveOpenAiSettings openAiStatus,
            DealGroupAiReviewService.BatchCandidatePreviewResponse batchPreview,
            SecDiscoveryScanner.SecDiscoveryStatus secDiscoveryStatus
    ) {
        java.util.ArrayList<String> warnings = new java.util.ArrayList<>();
        if (appStatus.latestScan() != null && appStatus.latestScan().finishedAt() == null) {
            warnings.add("Latest scan has no finishedAt timestamp.");
        }
        if (openAiStatus.enabled() && !openAiStatus.configured()) {
            warnings.add("OpenAI AI Review is enabled but not configured.");
        }
        if (appStatus.config().telegramEnabled() && !appStatus.config().telegramConfigured()) {
            warnings.add("Telegram is enabled but not configured.");
        }
        if (batchPreview.eligibleCount() == 0) {
            warnings.add("No strict AI candidates are available right now.");
        }
        if (!schedulerStatus.fullRefreshSchedulerEnabled()) {
            warnings.add("Scheduled Full Refresh is disabled; Full Refresh runs only on manual click.");
        }
        if (!secDiscoveryStatus.enabled()) {
            warnings.add("SEC Discovery is disabled; broad SEC discovery runs only when explicitly enabled.");
        }
        if (secDiscoveryStatus.enabled() && hasSecDiscoveryErrors(secDiscoveryStatus)) {
            warnings.add("SEC Discovery last run reported errors — check SEC Discovery status for details.");
        }
        return List.copyOf(warnings);
    }

    private boolean hasSecDiscoveryErrors(SecDiscoveryScanner.SecDiscoveryStatus status) {
        if (status.lastRunSummary() == null) {
            return false;
        }
        return !status.lastRunSummary().errors().isEmpty()
                || "FAILED".equals(status.lastRunStatus());
    }

    private String toMarkdown(ReviewPacket packet) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ParserNews Review Packet\n\n");
        line(builder, "Generated", packet.generatedAt());
        builder.append("\n");
        appendStatus(builder, packet);
        appendScheduler(builder, packet.schedulerStatus());
        appendSecDiscovery(builder, packet.secDiscoveryStatus());
        appendOpenAi(builder, packet.openAiStatus());
        appendTelegram(builder, packet.telegramStatus());
        appendDealGroupStats(builder, packet.dealGroupStats());
        appendAiSummary(builder, packet.aiReviewSummary());
        appendSourceQualityAudit(builder, packet.sourceQualityAudit());
        appendBatchPreview(builder, packet.batchAiCandidatesPreview());
        appendTopDealGroups(builder, packet.topDealGroups());
        appendScanRuns(builder, packet.recentScanRuns());
        appendKnownWarnings(builder, packet.knownWarnings());
        return builder.toString();
    }

    private void appendStatus(StringBuilder builder, ReviewPacket packet) {
        StatusService.StatusResponse status = packet.appStatus();
        builder.append("## App Status\n\n");
        line(builder, "health", status.status());
        line(builder, "scannerMonitoringEnabled", status.config().scannerMonitoringEnabled());
        line(builder, "alertDispatchEnabled", status.config().alertDispatchEnabled());
        line(builder, "telegramEnabled", status.config().telegramEnabled());
        line(builder, "telegramConfigured", status.config().telegramConfigured());
        if (status.latestScan() != null) {
            builder.append("\n### Latest Scan\n\n");
            line(builder, "id", status.latestScan().id());
            line(builder, "status", status.latestScan().status());
            line(builder, "startedAt", status.latestScan().startedAt());
            line(builder, "finishedAt", status.latestScan().finishedAt());
            line(builder, "triggerType", status.latestScan().triggerType());
            line(builder, "totalFetched", status.latestScan().totalFetched());
            line(builder, "candidatesFound", status.latestScan().candidatesFound());
            line(builder, "duplicatesSkipped", status.latestScan().duplicatesSkipped());
        }
        builder.append("\n### Counts\n\n");
        line(builder, "savedArticles", status.articleEvents().totalSavedArticles());
        line(builder, "detectedCandidates", status.articleEvents().totalDetectedCandidates());
        line(builder, "highCandidates", status.articleEvents().highCandidateCount());
        line(builder, "mediumCandidates", status.articleEvents().mediumCandidateCount());
        line(builder, "lowCandidates", status.articleEvents().lowCandidateCount());
        line(builder, "alertEligible", status.alerts().alertEligibleCount());
        line(builder, "alertQueued", status.alerts().alreadyQueuedCount());
        builder.append("\n");
    }

    private void appendSourceQualityAudit(StringBuilder builder, SourceQualityAudit audit) {
        builder.append("## Source Quality Audit\n\n");
        line(builder, "totalConfiguredSources", audit.totalConfiguredSources());
        line(builder, "KEEP", audit.keepCount());
        line(builder, "NEEDS_REVIEW", audit.needsReviewCount());
        line(builder, "DISABLE", audit.disableCount());
        line(builder, "strictCandidateCountTotal", audit.strictCandidateCountTotal());
        builder.append("\n");
        builder.append("| sourceName | fetched | candidates | strict | noise | recommendation | errors |\n");
        builder.append("|---|---:|---:|---:|---:|---|---:|\n");
        if (audit.sources().isEmpty()) {
            builder.append("| none | 0 | 0 | 0 | 0 | NEEDS_REVIEW | 0 |\n");
        } else {
            audit.sources().forEach(source -> builder.append("| ")
                    .append(tableCell(source.sourceName()))
                    .append(" | ")
                    .append(source.fetchedCount())
                    .append(" | ")
                    .append(source.candidateCount())
                    .append(" | ")
                    .append(source.strictCandidateCount())
                    .append(" | ")
                    .append(source.noiseCount())
                    .append(" | ")
                    .append(source.recommendation())
                    .append(" | ")
                    .append(source.errors() == null ? 0 : source.errors().size())
                    .append(" |\n"));
        }
        builder.append("\n");
    }

    private void appendScheduler(StringBuilder builder, SchedulerStatusService.SchedulerStatusResponse status) {
        builder.append("## Scheduler Status\n\n");
        line(builder, "rssMonitoringEnabled", status.rssMonitoringEnabled());
        line(builder, "fullRefreshSchedulerEnabled", status.fullRefreshSchedulerEnabled());
        line(builder, "fullRefreshSchedulerRunning", status.fullRefreshSchedulerRunning());
        line(builder, "lastScheduledFullRefreshStartedAt", status.lastScheduledFullRefreshStartedAt());
        line(builder, "lastScheduledFullRefreshFinishedAt", status.lastScheduledFullRefreshFinishedAt());
        line(builder, "lastScheduledFullRefreshSuccess", status.lastScheduledFullRefreshSuccess());
        line(builder, "nextExpectedFullRefreshAt", status.nextExpectedFullRefreshAt());
        line(builder, "message", status.message());
        if (status.latestScheduledRssScan() != null) {
            builder.append("\n### Latest Scheduled RSS Scan\n\n");
            line(builder, "id", status.latestScheduledRssScan().id());
            line(builder, "status", status.latestScheduledRssScan().status());
            line(builder, "startedAt", status.latestScheduledRssScan().startedAt());
            line(builder, "finishedAt", status.latestScheduledRssScan().finishedAt());
        }
        builder.append("\n");
    }

    private void appendSecDiscovery(StringBuilder builder, SecDiscoveryScanner.SecDiscoveryStatus status) {
        builder.append("## SEC Discovery Status\n\n");
        line(builder, "enabled", status.enabled());
        line(builder, "schedulerEnabled", status.schedulerEnabled());
        line(builder, "forms", String.join(", ", status.forms()));
        line(builder, "maxFilingsPerRun", status.maxFilingsPerRun());
        line(builder, "fetchPrimaryDocument", status.fetchPrimaryDocument());
        line(builder, "lastRunAt", status.lastRunAt());
        line(builder, "lastRunStatus", status.lastRunStatus());
        if (status.lastRunSummary() != null) {
            builder.append("\n### Last SEC Discovery Run\n\n");
            line(builder, "scannedFilings", status.lastRunSummary().scannedFilings());
            line(builder, "newFilings", status.lastRunSummary().newFilings());
            line(builder, "duplicateFilings", status.lastRunSummary().duplicateFilings());
            line(builder, "createdOrUpdatedDealGroups", status.lastRunSummary().createdOrUpdatedDealGroups());
            line(builder, "skippedFilings", status.lastRunSummary().skippedFilings());
            line(builder, "errors", status.lastRunSummary().errors().size());
        }
        line(builder, "warning", status.warning());
        builder.append("\n");
    }

    private void appendOpenAi(StringBuilder builder, SafeOpenAiStatus status) {
        builder.append("## OpenAI Status\n\n");
        line(builder, "enabled", status.enabled());
        line(builder, "configured", status.configured());
        line(builder, "keySource", status.keySource());
        line(builder, "keyMasked", status.keyMasked());
        line(builder, "model", status.model());
        line(builder, "maxInputChars", status.maxInputChars());
        line(builder, "message", status.message());
        builder.append("\n");
    }

    private void appendTelegram(StringBuilder builder, TelegramStatus status) {
        builder.append("## Telegram Status\n\n");
        line(builder, "enabled", status.enabled());
        line(builder, "configured", status.configured());
        line(builder, "tokenSource", status.tokenSource());
        line(builder, "tokenMasked", status.tokenMasked());
        line(builder, "chatIdSource", status.chatIdSource());
        line(builder, "chatIdMasked", status.chatIdMasked());
        line(builder, "dispatchEnabled", status.dispatchEnabled());
        line(builder, "message", status.message());
        builder.append("\n");
    }

    private void appendDealGroupStats(StringBuilder builder, DealGroupController.DealGroupStatsResponse stats) {
        builder.append("## Deal Group Quality Stats\n\n");
        line(builder, "totalGroups", stats.totalGroups());
        line(builder, "pendingGroups", stats.pendingGroups());
        line(builder, "usefulGroups", stats.usefulGroups());
        line(builder, "ignoredGroups", stats.ignoredGroups());
        line(builder, "highPriorityGroups", stats.highPriorityGroups());
        line(builder, "alertLikeGroups", stats.alertLikeGroups());
        line(builder, "groupedEvidenceTotal", stats.groupedEvidenceTotal());
        line(builder, "averageEvidencePerGroup", String.format(java.util.Locale.ROOT, "%.2f", stats.averageEvidencePerGroup()));
        mapSection(builder, "Review reasons", stats.reviewReasonBreakdown());
        mapSection(builder, "By relevance", stats.byDealRelevance());
        mapSection(builder, "By tradability", stats.byTradability());
        mapSection(builder, "By stage", stats.byDealStage());
        mapSection(builder, "By timing", stats.byDealTiming());
        mapSection(builder, "By priority", stats.byPriority());
        builder.append("\n");
    }

    private void appendAiSummary(StringBuilder builder, DealGroupAiReviewService.AiReviewSummaryResponse summary) {
        builder.append("## AI Review Summary\n\n");
        line(builder, "uniqueGroupsReviewed", summary.uniqueGroupsReviewed());
        line(builder, "totalAiReviewsSaved", summary.totalAiReviewsSaved());
        line(builder, "duplicateHistoricalReviewsIgnored", summary.duplicateHistoricalReviewsIgnored());
        line(builder, "goodSignalCount", summary.goodSignalCount());
        line(builder, "notTradableCount", summary.notTradableCount());
        line(builder, "privateCompanyCount", summary.privateCompanyCount());
        line(builder, "falsePositiveCount", summary.falsePositiveCount());
        line(builder, "needsHumanReviewCount", summary.needsHumanReviewCount());
        line(builder, "unknownCount", summary.unknownCount());
        line(builder, "highConfidenceCount", summary.highConfidenceCount());
        line(builder, "mediumConfidenceCount", summary.mediumConfidenceCount());
        line(builder, "lowConfidenceCount", summary.lowConfidenceCount());
        builder.append("\n### Latest AI Reviews\n\n");
        if (summary.latestReviews().isEmpty()) {
            builder.append("- none\n");
        } else {
            summary.latestReviews().stream().limit(10).forEach(review -> builder.append("- ")
                    .append(clean(review.groupKey()))
                    .append(" | ")
                    .append(clean(review.title()))
                    .append(" | ")
                    .append(review.verdict())
                    .append(" / ")
                    .append(review.confidence())
                    .append(" | prompt=")
                    .append(clean(review.promptVersion()))
                    .append(" | ")
                    .append(clean(review.reason()))
                    .append("\n"));
        }
        builder.append("\n");
    }

    private void appendBatchPreview(StringBuilder builder, DealGroupAiReviewService.BatchCandidatePreviewResponse preview) {
        builder.append("## Batch AI Candidates Preview\n\n");
        line(builder, "eligibleCount", preview.eligibleCount());
        line(builder, "requestedLimit", preview.requestedLimit());
        builder.append("\n");
        preview.candidates().stream()
                .filter(DealGroupAiReviewService.BatchCandidatePreviewItem::included)
                .limit(25)
                .forEach(candidate -> builder.append("- ")
                        .append(clean(candidate.groupKey()))
                        .append(" | ")
                        .append(clean(candidate.title()))
                        .append(" | ")
                        .append(candidate.priority())
                        .append(" | ")
                        .append(candidate.dealRelevance())
                        .append(" | ")
                        .append(candidate.tradability())
                        .append(" | ")
                        .append(candidate.dealTiming())
                        .append(" | ")
                        .append(clean(candidate.reasonIncluded()))
                        .append("\n"));
        if (preview.eligibleCount() == 0) {
            builder.append("- No promising groups need AI review right now.\n");
        }
        builder.append("\n");
    }

    private void appendTopDealGroups(StringBuilder builder, List<DealGroupingService.DealGroupResponse> groups) {
        builder.append("## Top Deal Groups\n\n");
        if (groups.isEmpty()) {
            builder.append("- none\n\n");
            return;
        }
        for (DealGroupingService.DealGroupResponse group : groups) {
            builder.append("### ").append(clean(group.title())).append("\n\n");
            line(builder, "groupKey", group.groupKey());
            line(builder, "buyer", group.buyerCompany());
            line(builder, "target", group.targetCompany());
            line(builder, "buyerTicker/CIK", joinPair(group.buyerTicker(), group.buyerCik()));
            line(builder, "targetTicker/CIK", joinPair(group.targetTicker(), group.targetCik()));
            line(builder, "priority", group.priority());
            line(builder, "dealRelevance", group.dealRelevance());
            line(builder, "tradability", group.tradability());
            line(builder, "dealStage", group.dealStage());
            line(builder, "dealTiming", group.dealTiming());
            line(builder, "review", joinPair(group.reviewStatus(), group.reviewReason()));
            line(builder, "evidenceCount", group.relatedSignals().size());
            line(builder, "warnings", String.join("; ", group.warnings()));
            builder.append("- evidenceUrls:\n");
            group.evidenceUrls().forEach(url -> builder.append("  - ").append(clean(url)).append("\n"));
            builder.append("\n");
        }
    }

    private void appendScanRuns(StringBuilder builder, List<ScanRunSummary> scanRuns) {
        builder.append("## Recent Scan Runs\n\n");
        if (scanRuns.isEmpty()) {
            builder.append("- none\n\n");
            return;
        }
        scanRuns.forEach(run -> builder.append("- #")
                .append(run.id())
                .append(" | ")
                .append(run.status())
                .append(" | ")
                .append(run.triggerType())
                .append(" | fetched=")
                .append(run.totalFetched())
                .append(" | candidates=")
                .append(run.candidatesFound())
                .append(" | duplicates=")
                .append(run.duplicatesSkipped())
                .append(" | finishedAt=")
                .append(run.finishedAt())
                .append("\n"));
        builder.append("\n");
    }

    private void appendKnownWarnings(StringBuilder builder, List<String> warnings) {
        builder.append("## Known Warnings\n\n");
        if (warnings.isEmpty()) {
            builder.append("- none\n");
        } else {
            warnings.forEach(warning -> builder.append("- ").append(clean(warning)).append("\n"));
        }
    }

    private void mapSection(StringBuilder builder, String title, Map<String, Long> values) {
        builder.append("\n### ").append(title).append("\n\n");
        if (values.isEmpty()) {
            builder.append("- none\n");
            return;
        }
        values.forEach((key, value) -> builder.append("- ").append(clean(key)).append(": ").append(value).append("\n"));
    }

    private void line(StringBuilder builder, String label, Object value) {
        builder.append("- **")
                .append(label)
                .append(":** ")
                .append(clean(value))
                .append("\n");
    }

    private String joinPair(Object first, Object second) {
        return clean(first) + " / " + clean(second);
    }

    private String clean(Object value) {
        if (value == null) {
            return "-";
        }
        return value.toString()
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
    }

    private String tableCell(Object value) {
        return clean(value).replace("|", "\\|");
    }

    public record ReviewPacket(
            Instant generatedAt,
            StatusService.StatusResponse appStatus,
            SchedulerStatusService.SchedulerStatusResponse schedulerStatus,
            SecDiscoveryScanner.SecDiscoveryStatus secDiscoveryStatus,
            SafeOpenAiStatus openAiStatus,
            TelegramStatus telegramStatus,
            DealGroupController.DealGroupStatsResponse dealGroupStats,
            DealGroupAiReviewService.AiReviewSummaryResponse aiReviewSummary,
            SourceQualityAudit sourceQualityAudit,
            DealGroupAiReviewService.BatchCandidatePreviewResponse batchAiCandidatesPreview,
            List<DealGroupingService.DealGroupResponse> topDealGroups,
            List<ScanRunSummary> recentScanRuns,
            List<String> knownWarnings
    ) {
    }

    public record SourceQualityAudit(
            int totalConfiguredSources,
            long keepCount,
            long needsReviewCount,
            long disableCount,
            long strictCandidateCountTotal,
            List<SourceEvaluationPreviewService.SourceEvaluationSummary> sources
    ) {
    }

    public record SafeOpenAiStatus(
            boolean enabled,
            boolean configured,
            OpenAiRuntimeSettingsService.KeySource keySource,
            String keyMasked,
            String model,
            int maxInputChars,
            String message
    ) {
    }

    public record TelegramStatus(
            boolean enabled,
            boolean configured,
            TelegramRuntimeSettingsService.SecretSource tokenSource,
            TelegramRuntimeSettingsService.SecretSource chatIdSource,
            String tokenMasked,
            String chatIdMasked,
            boolean dispatchEnabled,
            String message
    ) {
    }

    public record ScanRunSummary(
            Long id,
            Instant startedAt,
            Instant finishedAt,
            Object triggerType,
            Object status,
            int totalFetched,
            int candidatesFound,
            int savedArticles,
            int duplicatesSkipped,
            int errorsCount,
            String errorMessage
    ) {
        static ScanRunSummary from(ScanRunEntity scanRun) {
            return new ScanRunSummary(
                    scanRun.getId(),
                    scanRun.getStartedAt(),
                    scanRun.getFinishedAt(),
                    scanRun.getTriggerType(),
                    scanRun.getStatus(),
                    scanRun.getTotalFetched(),
                    scanRun.getCandidatesFound(),
                    scanRun.getSavedArticles(),
                    scanRun.getDuplicatesSkipped(),
                    scanRun.getErrorsCount(),
                    scanRun.getErrorMessage()
            );
        }
    }
}
