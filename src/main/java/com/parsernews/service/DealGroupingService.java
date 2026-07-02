package com.parsernews.service;

import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealStage;
import com.parsernews.model.DealTiming;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DealGroupReviewEntity;
import com.parsernews.persistence.DealGroupReviewRepository;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.persistence.SecSignalPriority;
import com.parsernews.web.SignalInboxController.SourceType;
import com.parsernews.web.SignalInboxController.UnifiedPriority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DealGroupingService {
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private final DetectedEventRepository eventRepository;
    private final SecFilingRepository secFilingRepository;
    private final CandidateReviewInsightService reviewInsightService;
    private final DealTermsExtractionService dealTermsExtractionService;
    private final DealRelevanceService dealRelevanceService;
    private final DealStageDetectionService dealStageDetectionService;
    private final AlertEligibilityService alertEligibilityService;
    private final DealGroupReviewRepository dealGroupReviewRepository;

    public DealGroupingService(
            DetectedEventRepository eventRepository,
            SecFilingRepository secFilingRepository,
            CandidateReviewInsightService reviewInsightService,
            DealTermsExtractionService dealTermsExtractionService,
            DealRelevanceService dealRelevanceService,
            DealStageDetectionService dealStageDetectionService,
            AlertEligibilityService alertEligibilityService,
            DealGroupReviewRepository dealGroupReviewRepository
    ) {
        this.eventRepository = eventRepository;
        this.secFilingRepository = secFilingRepository;
        this.reviewInsightService = reviewInsightService;
        this.dealTermsExtractionService = dealTermsExtractionService;
        this.dealRelevanceService = dealRelevanceService;
        this.dealStageDetectionService = dealStageDetectionService;
        this.alertEligibilityService = alertEligibilityService;
        this.dealGroupReviewRepository = dealGroupReviewRepository;
    }

    @Transactional(readOnly = true)
    public List<DealGroupResponse> groups(ManualReviewStatus reviewStatus, UnifiedPriority priority, int limit) {
        List<GroupBuilder> builders = buildGroups();
        return builders.stream()
                .map(this::toResponse)
                .filter(group -> reviewStatus == null
                        ? group.reviewStatus() != ManualReviewStatus.IGNORED
                        : group.reviewStatus() == reviewStatus)
                .filter(group -> priority == null || group.priority() == priority)
                .sorted(groupComparator())
                .limit(normalizedLimit(limit))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<DealGroupResponse> group(String groupKey) {
        return buildGroups().stream()
                .map(this::toResponse)
                .filter(group -> group.groupKey().equals(groupKey))
                .findFirst();
    }

    public String formatTelegramPreview(DealGroupResponse group) {
        StringBuilder b = new StringBuilder();

        // ── Header ──────────────────────────────────────────────────────────
        String ticker = firstNonBlank(group.targetTicker(), group.buyerTicker(), null);
        String tickerLabel = (ticker != null && !"UNKNOWN".equalsIgnoreCase(ticker)) ? " <b>$" + ticker + "</b>" : "";
        b.append("📊 <b>M&amp;A Signal</b>").append(tickerLabel).append('\n');

        // ── Companies ───────────────────────────────────────────────────────
        String targetName = firstNonBlank(group.targetCompany(), null);
        String buyerName  = firstNonBlank(group.buyerCompany(), null);
        if (targetName != null) {
            b.append("<b>Target:</b> ").append(escapeHtml(targetName));
            if (!isBlank(group.targetTicker()) && !"UNKNOWN".equalsIgnoreCase(group.targetTicker())) {
                b.append(" ($").append(group.targetTicker()).append(")");
            }
            if (!isBlank(group.targetCik())) {
                b.append("  <i>CIK ").append(group.targetCik()).append("</i>");
            }
            b.append('\n');
        }
        if (buyerName != null) {
            b.append("<b>Buyer:</b> ").append(escapeHtml(buyerName));
            if (!isBlank(group.buyerTicker()) && !"UNKNOWN".equalsIgnoreCase(group.buyerTicker())) {
                b.append(" ($").append(group.buyerTicker()).append(")");
            }
            b.append('\n');
        }
        if (targetName == null && buyerName == null) {
            b.append("<i>Parties unknown — see filing below</i>\n");
        }

        // ── Deal classification ──────────────────────────────────────────────
        b.append('\n');
        String relevance  = stringValue(group.dealRelevance());
        String tradability = stringValue(group.tradability());
        String stage      = stringValue(group.dealStage());
        String timing     = stringValue(group.dealTiming());
        if (relevance != null)  b.append("<b>Deal type:</b> ").append(relevance).append('\n');
        b.append("<b>Priority:</b> ").append(group.priority())
                .append("  ·  Tradability: ").append(firstNonBlank(tradability, "?"))
                .append("  ·  Stage: ").append(firstNonBlank(stage, "?"));
        if (timing != null) b.append(" (").append(timing.toLowerCase(Locale.ROOT)).append(")");
        b.append('\n');

        // ── Top trigger signal ───────────────────────────────────────────────
        b.append('\n');
        group.relatedSignals().stream().limit(1).forEach(signal -> {
            String src = signal.sourceType() != null ? signal.sourceType().name() : "UNKNOWN";
            String sig = firstNonBlank(signal.signalType(), null);
            b.append("📡 <b>Triggered by:</b> ").append(src.replace("_", " "));
            if (sig != null) b.append(" · ").append(sig.replace("_", " "));
            b.append('\n');
            String title = firstNonBlank(signal.title(), null);
            if (title != null) {
                String truncated = title.length() > 120 ? title.substring(0, 117) + "…" : title;
                b.append("<i>").append(escapeHtml(truncated)).append("</i>\n");
            }
            String url = firstNonBlank(signal.url(), null);
            if (url != null) b.append("🔗 ").append(escapeHtml(url)).append('\n');
        });

        // extra signals summary
        int extra = group.relatedSignals().size() - 1;
        if (extra > 0) {
            b.append("<i>+").append(extra).append(" more signal").append(extra > 1 ? "s" : "").append("</i>\n");
        }

        return b.toString().trim();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private DealGroupResponse toResponse(GroupBuilder builder) {
        DealGroupResponse derived = builder.toResponse();
        return dealGroupReviewRepository.findByGroupKey(derived.groupKey())
                .map(review -> withReview(derived, review))
                .orElse(derived);
    }

    private DealGroupResponse withReview(DealGroupResponse group, DealGroupReviewEntity review) {
        return new DealGroupResponse(
                group.groupKey(),
                group.primarySignalSourceType(),
                group.primarySignalId(),
                group.title(),
                group.buyerCompany(),
                group.targetCompany(),
                group.targetTicker(),
                group.targetCik(),
                group.buyerTicker(),
                group.buyerCik(),
                group.priority(),
                group.dealRelevance(),
                group.tradability(),
                group.dealStage(),
                group.dealTiming(),
                review.getManualReviewStatus(),
                review.getManualReviewReason(),
                review.getManualReviewNote(),
                review.getManualReviewedAt(),
                true,
                group.relatedSignals(),
                group.evidenceUrls(),
                group.warnings(),
                group.sortInstant()
        );
    }

    private List<GroupBuilder> buildGroups() {
        Map<String, GroupBuilder> groupsByKey = new LinkedHashMap<>();
        List<GroupBuilder> groups = new ArrayList<>();

        for (DetectedEventEntity event : eventRepository.findTop200ByOrderByDetectedAtDesc()) {
            if (event.getCandidateStrength() == CandidateStrength.NONE) {
                continue;
            }
            RssDealSignal signal = rssSignal(event);
            String key = rssGroupKey(signal);
            GroupBuilder group = groupsByKey.computeIfAbsent(key, unused -> {
                GroupBuilder created = GroupBuilder.fromRss(key, signal);
                groups.add(created);
                return created;
            });
            if (!group.hasSignal(SourceType.RSS_NEWS, event.getId())) {
                group.addRss(signal, "RSS signal shares the same deal target or exact buyer/target names.");
            }
        }

        for (SecFilingEntity filing : secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()) {
            SecDealSignal signal = secSignal(filing);
            Match match = findSecMatch(groups, signal);
            if (match != null) {
                match.group().addSec(signal, match.reason());
                continue;
            }
            String key = "sec:" + normalizeCik(signal.cik()) + ":" + normalizeKey(signal.accessionNumber());
            groups.add(GroupBuilder.fromSec(key, signal));
        }

        return groups;
    }

    private RssDealSignal rssSignal(DetectedEventEntity event) {
        NewsArticleEntity article = event.getArticle();
        CandidateReviewInsightService.ReviewInsight insight = reviewInsightService.insight(article, event);
        DealTermsExtractionService.DealTerms terms = dealTermsExtractionService.extract(article, event, insight);
        DealRelevanceService.RelevanceInsight relevance = dealRelevanceService.assess(article, event, insight, terms);
        DealStageDetectionService.StageInsight stage = dealStageDetectionService.detect(article, event, terms, insight, relevance);
        AlertEligibilityService.AlertEligibility alertEligibility = alertEligibilityService.evaluate(event);
        return new RssDealSignal(
                event.getId(),
                article.getId(),
                article.getHeadline(),
                article.getUrl(),
                article.getPublishedAt(),
                article.getCollectedAt(),
                terms.buyerCompany(),
                terms.targetCompany(),
                event.getBuyerTicker(),
                event.getBuyerCik(),
                event.getTargetTicker(),
                event.getTargetCik(),
                priorityFromCandidateStrength(event.getCandidateStrength()),
                event.getEventType() == null ? "UNKNOWN" : event.getEventType().name(),
                insight.reviewSummary(),
                event.getManualReviewStatus(),
                event.getManualReviewReason(),
                relevance.dealRelevance(),
                relevance.tradability(),
                stage.dealStage(),
                stage.dealTiming(),
                alertEligibility.eligible()
        );
    }

    private SecDealSignal secSignal(SecFilingEntity filing) {
        return new SecDealSignal(
                filing.getId(),
                filing.getCompanyName() + " " + filing.getForm(),
                firstNonBlank(filing.getDocumentUrl(), filing.getFilingUrl()),
                filing.getCompanyName(),
                filing.getCik(),
                filing.getForm(),
                filing.getFilingDate(),
                filing.getProcessedAt(),
                filing.getAccessionNumber(),
                priorityFromSecPriority(filing.getSecSignalPriority()),
                filing.getSecSignalType().name(),
                firstNonBlank(filing.getSecSignalSummary(), filing.getSignalReason()),
                firstNonBlank(filing.getDocumentTextSnippet(), filing.getDocumentSignalReason()),
                filing.getManualReviewStatus(),
                filing.getManualReviewReason()
        );
    }

    private String rssGroupKey(RssDealSignal signal) {
        String targetCik = normalizeCik(signal.targetCik());
        if (!targetCik.isBlank()) {
            return "target-cik:" + targetCik;
        }
        String targetTicker = normalizeTicker(signal.targetTicker());
        if (!targetTicker.isBlank()) {
            return "target-ticker:" + targetTicker;
        }
        String buyer = normalizeCompany(signal.buyerCompany());
        String target = normalizeCompany(signal.targetCompany());
        if (!buyer.isBlank() && !target.isBlank()) {
            return "names:" + buyer + ":" + target;
        }
        if (!target.isBlank()) {
            return "target-name:" + target;
        }
        String normalizedTitle = normalizeTitle(signal.title());
        if (!normalizedTitle.isBlank()) {
            return "title:" + normalizedTitle;
        }
        return "rss:" + signal.id();
    }

    private Match findSecMatch(List<GroupBuilder> groups, SecDealSignal signal) {
        String cik = normalizeCik(signal.cik());
        String company = normalizeCompany(signal.companyName());
        for (GroupBuilder group : groups) {
            if (!cik.isBlank() && cik.equals(normalizeCik(group.targetCik))) {
                return new Match(group, "same target CIK");
            }
            if (!company.isBlank() && company.equals(normalizeCompany(group.targetCompany))) {
                return new Match(group, "SEC company matches RSS target company");
            }
            if (!cik.isBlank()
                    && cik.equals(normalizeCik(group.buyerCik))
                    && mentions(signal.searchText(), group.targetCompany, group.targetTicker)) {
                return new Match(group, "same buyer CIK and SEC document mentions target");
            }
            if (!company.isBlank()
                    && company.equals(normalizeCompany(group.buyerCompany))
                    && mentions(signal.searchText(), group.targetCompany, group.targetTicker)) {
                return new Match(group, "SEC company matches buyer and document mentions target");
            }
        }
        return null;
    }

    private Comparator<DealGroupResponse> groupComparator() {
        return Comparator
                .comparingInt((DealGroupResponse group) -> priorityRank(group.priority())).reversed()
                .thenComparing(group -> group.relatedSignals().stream()
                        .anyMatch(signal -> signal.sourceType() == SourceType.RSS_NEWS && signal.priority() == UnifiedPriority.HIGH), Comparator.reverseOrder())
                .thenComparing(DealGroupResponse::sortInstant, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(group -> group.reviewStatus() == ManualReviewStatus.IGNORED);
    }

    private boolean mentions(String text, String company, String ticker) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String normalizedCompany = normalizeCompany(company);
        if (!normalizedCompany.isBlank() && normalizeCompany(lower).contains(normalizedCompany)) {
            return true;
        }
        String normalizedTicker = normalizeTicker(ticker);
        return !normalizedTicker.isBlank() && lower.contains(normalizedTicker.toLowerCase(Locale.ROOT));
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
            case NONE, UNKNOWN -> UnifiedPriority.NONE;
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

    private int normalizedLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 200);
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String normalizeTicker(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private static String normalizeCik(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("\\D", "");
        String normalized = digits.replaceFirst("^0+(?!$)", "");
        return normalized.isBlank() ? "" : normalized;
    }

    private static String normalizeCompany(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("\\b(incorporated|inc|corp|corporation|company|co|ltd|limited|llc|plc|holdings|holding|class a|common stock)\\b", " ");
        return NON_ALNUM.matcher(lower).replaceAll(" ").trim().replaceAll("\\s+", " ");
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : NON_ALNUM.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-");
    }

    private static String normalizeTitle(String value) {
        String normalized = normalizeCompany(value)
                .replaceAll("\\b(announces|announce|enters|entered|definitive|agreement|merger|acquisition|acquire|acquires|to|for|with|and|the|a|an)\\b", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized.length() < 6 || normalized.split(" ").length < 2 ? "" : normalized;
    }

    private record RssDealSignal(
            Long id,
            Long articleId,
            String title,
            String url,
            Instant publishedAt,
            Instant discoveredAt,
            String buyerCompany,
            String targetCompany,
            String buyerTicker,
            String buyerCik,
            String targetTicker,
            String targetCik,
            UnifiedPriority priority,
            String signalType,
            String summary,
            ManualReviewStatus reviewStatus,
            ManualReviewReason reviewReason,
            DealRelevance dealRelevance,
            Tradability tradability,
            DealStage dealStage,
            DealTiming dealTiming,
            boolean alertEligible
    ) {
        Instant sortInstant() {
            return publishedAt != null ? publishedAt : discoveredAt;
        }
    }

    private record SecDealSignal(
            Long id,
            String title,
            String url,
            String companyName,
            String cik,
            String form,
            LocalDate filingDate,
            Instant processedAt,
            String accessionNumber,
            UnifiedPriority priority,
            String signalType,
            String summary,
            String searchText,
            ManualReviewStatus reviewStatus,
            ManualReviewReason reviewReason
    ) {
        Instant sortInstant() {
            return processedAt;
        }
    }

    private record Match(GroupBuilder group, String reason) {
    }

    private static final class GroupBuilder {
        private final String groupKey;
        private Long primarySignalId;
        private SourceType primarySignalSourceType;
        private String title;
        private String buyerCompany;
        private String targetCompany;
        private String targetTicker;
        private String targetCik;
        private String buyerTicker;
        private String buyerCik;
        private UnifiedPriority priority = UnifiedPriority.NONE;
        private DealRelevance dealRelevance;
        private Tradability tradability;
        private DealStage dealStage;
        private DealTiming dealTiming;
        private ManualReviewStatus reviewStatus = ManualReviewStatus.PENDING;
        private ManualReviewReason reviewReason;
        private Instant sortInstant;
        private final List<RelatedSignalResponse> relatedSignals = new ArrayList<>();
        private final Set<String> evidenceUrls = new LinkedHashSet<>();
        private final Set<String> warnings = new LinkedHashSet<>();

        private GroupBuilder(String groupKey) {
            this.groupKey = groupKey;
        }

        static GroupBuilder fromRss(String groupKey, RssDealSignal signal) {
            GroupBuilder group = new GroupBuilder(groupKey);
            group.addRss(signal, "primary RSS deal signal");
            return group;
        }

        static GroupBuilder fromSec(String groupKey, SecDealSignal signal) {
            GroupBuilder group = new GroupBuilder(groupKey);
            group.addSec(signal, "standalone SEC filing signal");
            return group;
        }

        void addRss(RssDealSignal signal, String reason) {
            relatedSignals.add(new RelatedSignalResponse(
                    SourceType.RSS_NEWS,
                    signal.id(),
                    signal.title(),
                    signal.url(),
                    signal.sortInstant(),
                    null,
                    signal.signalType(),
                    signal.priority(),
                    reason
            ));
            evidenceUrls.add(signal.url());
            mergePrimary(signal);
            if (signal.alertEligible()) {
                warnings.add("RSS signal is alert eligible");
            }
        }

        void addSec(SecDealSignal signal, String reason) {
            relatedSignals.add(new RelatedSignalResponse(
                    SourceType.SEC_FILING,
                    signal.id(),
                    signal.title(),
                    signal.url(),
                    signal.sortInstant(),
                    signal.filingDate(),
                    signal.signalType(),
                    signal.priority(),
                    reason
            ));
            evidenceUrls.add(signal.url());
            mergePrimary(signal);
        }

        boolean hasSignal(SourceType sourceType, Long id) {
            return relatedSignals.stream().anyMatch(signal -> signal.sourceType() == sourceType && signal.id().equals(id));
        }

        private void mergePrimary(RssDealSignal signal) {
            buyerCompany = firstNonBlankStatic(buyerCompany, signal.buyerCompany());
            targetCompany = firstNonBlankStatic(targetCompany, signal.targetCompany());
            buyerTicker = firstNonBlankStatic(buyerTicker, signal.buyerTicker());
            buyerCik = firstNonBlankStatic(buyerCik, signal.buyerCik());
            targetTicker = firstNonBlankStatic(targetTicker, safeTargetTicker(signal));
            targetCik = firstNonBlankStatic(targetCik, safeTargetCik(signal));
            if (shouldReplacePrimary(signal.priority(), signal.sortInstant())) {
                primarySignalSourceType = SourceType.RSS_NEWS;
                primarySignalId = signal.id();
                title = signal.title();
                priority = signal.priority();
                dealRelevance = signal.dealRelevance();
                tradability = signal.tradability();
                dealStage = signal.dealStage();
                dealTiming = signal.dealTiming();
                reviewStatus = signal.reviewStatus();
                reviewReason = signal.reviewReason();
                sortInstant = signal.sortInstant();
            }
        }

        private void mergePrimary(SecDealSignal signal) {
            if (targetCompany == null || targetCompany.isBlank()) {
                targetCompany = signal.companyName();
                targetCik = firstNonBlankStatic(targetCik, signal.cik());
            }
            if (shouldReplacePrimary(signal.priority(), signal.sortInstant())) {
                primarySignalSourceType = SourceType.SEC_FILING;
                primarySignalId = signal.id();
                title = signal.title();
                priority = signal.priority();
                reviewStatus = signal.reviewStatus();
                reviewReason = signal.reviewReason();
                sortInstant = signal.sortInstant();
            }
        }

        private boolean shouldReplacePrimary(UnifiedPriority candidatePriority, Instant candidateInstant) {
            int candidateRank = priorityRankStatic(candidatePriority);
            int currentRank = priorityRankStatic(priority);
            if (primarySignalId == null || candidateRank > currentRank) {
                return true;
            }
            return candidateRank == currentRank
                    && candidateInstant != null
                    && (sortInstant == null || candidateInstant.isAfter(sortInstant));
        }

        DealGroupResponse toResponse() {
            if (relatedSignals.size() > 1) {
                warnings.add("Multiple related signals found for this deal");
            }
            return new DealGroupResponse(
                    groupKey,
                    primarySignalSourceType,
                    primarySignalId,
                    title,
                    buyerCompany,
                    targetCompany,
                    targetTicker,
                    targetCik,
                    buyerTicker,
                    buyerCik,
                    priority,
                    dealRelevance,
                    tradability,
                    dealStage,
                    dealTiming,
                    reviewStatus,
                    reviewReason,
                    null,
                    null,
                    false,
                    List.copyOf(relatedSignals),
                    List.copyOf(evidenceUrls),
                    List.copyOf(warnings),
                    sortInstant
            );
        }

        private static String firstNonBlankStatic(String first, String fallback) {
            return first == null || first.isBlank() ? fallback : first;
        }

        private static int priorityRankStatic(UnifiedPriority priority) {
            return switch (priority == null ? UnifiedPriority.NONE : priority) {
                case HIGH -> 4;
                case MEDIUM -> 3;
                case LOW -> 2;
                case NONE -> 1;
            };
        }

        private static String safeTargetTicker(RssDealSignal signal) {
            if (sameTicker(signal.targetTicker(), signal.buyerTicker())
                    && !sameCompany(signal.targetCompany(), signal.buyerCompany())) {
                return null;
            }
            return signal.targetTicker();
        }

        private static String safeTargetCik(RssDealSignal signal) {
            if ((sameTicker(signal.targetTicker(), signal.buyerTicker()) || sameCik(signal.targetCik(), signal.buyerCik()))
                    && !sameCompany(signal.targetCompany(), signal.buyerCompany())) {
                return null;
            }
            return signal.targetCik();
        }

        private static boolean sameTicker(String first, String second) {
            return first != null
                    && second != null
                    && normalizeTicker(first).equals(normalizeTicker(second));
        }

        private static boolean sameCik(String first, String second) {
            return first != null
                    && second != null
                    && normalizeCik(first).equals(normalizeCik(second));
        }

        private static boolean sameCompany(String first, String second) {
            return !normalizeCompany(first).isBlank()
                    && normalizeCompany(first).equals(normalizeCompany(second));
        }
    }

    public record DealGroupResponse(
            String groupKey,
            SourceType primarySignalSourceType,
            Long primarySignalId,
            String title,
            String buyerCompany,
            String targetCompany,
            String targetTicker,
            String targetCik,
            String buyerTicker,
            String buyerCik,
            UnifiedPriority priority,
            DealRelevance dealRelevance,
            Tradability tradability,
            DealStage dealStage,
            DealTiming dealTiming,
            ManualReviewStatus reviewStatus,
            ManualReviewReason reviewReason,
            String reviewNote,
            Instant reviewedAt,
            boolean groupReviewStored,
            List<RelatedSignalResponse> relatedSignals,
            List<String> evidenceUrls,
            List<String> warnings,
            Instant sortInstant
    ) {
    }

    public record RelatedSignalResponse(
            SourceType sourceType,
            Long id,
            String title,
            String url,
            Instant date,
            LocalDate filingDate,
            String signalType,
            UnifiedPriority priority,
            String relatedReason
    ) {
    }
}
