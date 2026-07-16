package com.parsernews.service;

import com.parsernews.model.AnalysisResult;
import com.parsernews.model.NewsEvent;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.ReviewStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-runs the rule analyzer over already-stored events.
 *
 * Articles are analyzed once, at scan time, and their verdict is then frozen in the database — so
 * when a rules gap is fixed, everything already collected keeps the old verdict forever. This
 * replays the current rules over stored articles so past articles get the verdict they would get
 * today.
 *
 * Only rule-derived fields are rewritten; manual review decisions, validation status, notes and
 * alert/dispatch history are left untouched.
 */
@Service
public class EventReanalysisService {
    private static final Logger log = LoggerFactory.getLogger(EventReanalysisService.class);

    private final DetectedEventRepository eventRepository;
    private final RuleBasedNewsAnalyzer analyzer;
    private final FalsePositiveFilter falsePositiveFilter;

    public EventReanalysisService(
            DetectedEventRepository eventRepository,
            RuleBasedNewsAnalyzer analyzer,
            FalsePositiveFilter falsePositiveFilter
    ) {
        this.eventRepository = eventRepository;
        this.analyzer = analyzer;
        this.falsePositiveFilter = falsePositiveFilter;
    }

    @Transactional
    public ReanalysisSummary reanalyze(boolean dryRun) {
        int scanned = 0;
        int changed = 0;
        List<StatusChange> changes = new ArrayList<>();

        for (DetectedEventEntity event : eventRepository.findAll()) {
            NewsArticleEntity article = event.getArticle();
            if (article == null || article.getHeadline() == null) {
                continue;
            }
            scanned++;
            NewsEvent newsEvent = new NewsEvent(
                    article.getTicker(),
                    article.getCompanyName(),
                    article.getHeadline(),
                    article.getArticleText() == null ? "" : article.getArticleText(),
                    article.getSource() == null ? "" : article.getSource().getName(),
                    article.getUrl()
            );
            AnalysisResult result = analyzer.analyze(newsEvent);
            ReviewStatus newStatus = EventPersistenceService.mapReviewStatus(result.status());
            ReviewStatus oldStatus = event.getReviewStatus();
            int oldScore = event.getConfidenceScore();

            if (newStatus != oldStatus || result.score() != oldScore) {
                changed++;
                changes.add(new StatusChange(
                        event.getId(),
                        article.getHeadline(),
                        oldStatus == null ? null : oldStatus.name(),
                        newStatus.name(),
                        oldScore,
                        result.score()
                ));
            }
            if (dryRun) {
                continue;
            }
            event.updateRuleAnalysis(
                    EventPersistenceService.mapEventType(result.eventType()),
                    newStatus,
                    result.score(),
                    firstNonBlank(result.targetTicker(), article.getTicker()),
                    result.acquirer(),
                    result.offerPrice(),
                    result.cashOrStock(),
                    result.premiumPercent(),
                    join(result.matchedPositiveKeywords()),
                    join(result.matchedNegativeKeywords()),
                    join(falsePositiveFilter.reasons(newsEvent.fullText())),
                    result.reason()
            );
        }
        if (!dryRun) {
            log.info("Re-analyzed {} stored events, {} changed", scanned, changed);
        }
        return new ReanalysisSummary(dryRun, scanned, changed, changes);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(", ", values);
    }

    public record ReanalysisSummary(
            boolean dryRun,
            int scannedEvents,
            int changedEvents,
            List<StatusChange> changes
    ) {
    }

    public record StatusChange(
            Long eventId,
            String headline,
            String oldStatus,
            String newStatus,
            int oldScore,
            int newScore
    ) {
    }
}
