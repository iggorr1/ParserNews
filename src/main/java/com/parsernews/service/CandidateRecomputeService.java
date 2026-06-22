package com.parsernews.service;

import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.NewsArticleEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class CandidateRecomputeService {
    private final DetectedEventRepository eventRepository;
    private final CandidateScoringService candidateScoringService;
    private final AlertEligibilityService alertEligibilityService;
    private final RssCompanyEnrichmentService rssCompanyEnrichmentService;

    public CandidateRecomputeService(
            DetectedEventRepository eventRepository,
            CandidateScoringService candidateScoringService,
            AlertEligibilityService alertEligibilityService,
            RssCompanyEnrichmentService rssCompanyEnrichmentService
    ) {
        this.eventRepository = eventRepository;
        this.candidateScoringService = candidateScoringService;
        this.alertEligibilityService = alertEligibilityService;
        this.rssCompanyEnrichmentService = rssCompanyEnrichmentService;
    }

    @Transactional
    public RecomputeSummary recomputeCandidates() {
        RecomputeCounter counter = new RecomputeCounter();
        for (DetectedEventEntity event : eventRepository.findAll()) {
            counter.scannedEvents++;
            NewsArticleEntity article = event.getArticle();
            CandidateScoringService.CandidateScore score = candidateScoringService.score(
                    article.getHeadline(),
                    article.getArticleText()
            );
            boolean alreadyQueued = event.getAlertQueuedAt() != null;
            event.updateCandidateScore(score.score(), score.strength(), score.reason());
            RssCompanyEnrichmentService.CompanyEnrichment enrichment = rssCompanyEnrichmentService.enrich(article, event);
            boolean enrichmentChanged = needsEnrichmentUpdate(event, enrichment);
            applyEnrichment(event, enrichment);
            AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);
            boolean alertEligible = eligibility.eligible();

            counter.countStrength(score.strength());
            if (alertEligible) {
                counter.alertEligibleCount++;
            }
            if (alreadyQueued) {
                counter.alreadyQueuedCount++;
            }
            if (enrichmentChanged || needsUpdate(event, score, alertEligible, eligibility.reason())) {
                counter.updatedEvents++;
            }

            event.updateAlertEligibility(alertEligible, eligibility.reason());
        }
        return counter.toSummary();
    }

    private void applyEnrichment(DetectedEventEntity event, RssCompanyEnrichmentService.CompanyEnrichment enrichment) {
        event.updateCompanyEnrichment(
                enrichment.target().ticker(),
                enrichment.target().cik(),
                enrichment.target().publicCompany(),
                enrichment.target().matchConfidence(),
                enrichment.buyer().ticker(),
                enrichment.buyer().cik(),
                enrichment.buyer().publicCompany(),
                enrichment.buyer().matchConfidence(),
                String.join("|", enrichment.warnings())
        );
    }

    private boolean needsEnrichmentUpdate(DetectedEventEntity event, RssCompanyEnrichmentService.CompanyEnrichment enrichment) {
        return !Objects.equals(event.getTargetTicker(), enrichment.target().ticker())
                || !Objects.equals(event.getTargetCik(), enrichment.target().cik())
                || event.isTargetPublicCompany() != enrichment.target().publicCompany()
                || event.getTargetMatchConfidence() != enrichment.target().matchConfidence()
                || !Objects.equals(event.getBuyerTicker(), enrichment.buyer().ticker())
                || !Objects.equals(event.getBuyerCik(), enrichment.buyer().cik())
                || event.isBuyerPublicCompany() != enrichment.buyer().publicCompany()
                || event.getBuyerMatchConfidence() != enrichment.buyer().matchConfidence()
                || !Objects.equals(event.getCompanyEnrichmentWarnings(), String.join("|", enrichment.warnings()));
    }

    private boolean needsUpdate(
            DetectedEventEntity event,
            CandidateScoringService.CandidateScore score,
            boolean alertEligible,
            String alertReason
    ) {
        return event.getCandidateScore() != score.score()
                || event.getCandidateStrength() != score.strength()
                || !Objects.equals(event.getCandidateReason(), score.reason())
                || event.isAlertEligible() != alertEligible
                || !Objects.equals(event.getAlertReason(), alertReason);
    }

    public record RecomputeSummary(
            int scannedEvents,
            int updatedEvents,
            int highCount,
            int mediumCount,
            int lowCount,
            int noneCount,
            int alertEligibleCount,
            int alreadyQueuedCount
    ) {
    }

    private static class RecomputeCounter {
        private int scannedEvents;
        private int updatedEvents;
        private int highCount;
        private int mediumCount;
        private int lowCount;
        private int noneCount;
        private int alertEligibleCount;
        private int alreadyQueuedCount;

        private void countStrength(CandidateStrength strength) {
            switch (strength) {
                case HIGH -> highCount++;
                case MEDIUM -> mediumCount++;
                case LOW -> lowCount++;
                case NONE -> noneCount++;
            }
        }

        private RecomputeSummary toSummary() {
            return new RecomputeSummary(
                    scannedEvents,
                    updatedEvents,
                    highCount,
                    mediumCount,
                    lowCount,
                    noneCount,
                    alertEligibleCount,
                    alreadyQueuedCount
            );
        }
    }
}
