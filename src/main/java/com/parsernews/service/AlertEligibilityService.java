package com.parsernews.service;

import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealStage;
import com.parsernews.model.DealTiming;
import com.parsernews.model.ReviewVerdict;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.ManualReviewStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Locale;

@Service
public class AlertEligibilityService {
    private static final List<String> TRUSTED_HOSTS = List.of(
            "globenewswire.com",
            "prnewswire.com",
            "sec.gov",
            "example.com"
    );

    private final CandidateReviewInsightService reviewInsightService;
    private final DealTermsExtractionService dealTermsExtractionService;
    private final DealRelevanceService dealRelevanceService;
    private final DealStageDetectionService dealStageDetectionService;

    public AlertEligibilityService() {
        this(
                new CandidateReviewInsightService(),
                new DealTermsExtractionService(),
                new DealRelevanceService(),
                new DealStageDetectionService()
        );
    }

    @Autowired
    public AlertEligibilityService(
            CandidateReviewInsightService reviewInsightService,
            DealTermsExtractionService dealTermsExtractionService,
            DealRelevanceService dealRelevanceService,
            DealStageDetectionService dealStageDetectionService
    ) {
        this.reviewInsightService = reviewInsightService;
        this.dealTermsExtractionService = dealTermsExtractionService;
        this.dealRelevanceService = dealRelevanceService;
        this.dealStageDetectionService = dealStageDetectionService;
    }

    public AlertEligibility evaluate(
            CandidateStrength candidateStrength,
            int candidateScore,
            String sourceUrl,
            boolean alreadyQueued
    ) {
        if (alreadyQueued) {
            return new AlertEligibility(false, "Candidate was already queued for alert.");
        }
        if (candidateStrength != CandidateStrength.HIGH) {
            return new AlertEligibility(false, "Candidate strength is not HIGH.");
        }
        if (candidateScore <= 0) {
            return new AlertEligibility(false, "Candidate score is not positive.");
        }
        if (!isTrustedHost(sourceUrl)) {
            return new AlertEligibility(false, "Source host is not trusted for alert queueing.");
        }
        return new AlertEligibility(true, "HIGH candidate from trusted source with positive score.");
    }

    public AlertEligibility evaluate(DetectedEventEntity event) {
        if (NewsTextPatterns.isRoundupAggregator(event.getArticle().getHeadline(), event.getArticle().getArticleText())) {
            return new AlertEligibility(false, "Roundup/aggregator article is not eligible for alert queueing.");
        }
        if (event.getAlertQueuedAt() != null) {
            return new AlertEligibility(false, "Candidate was already queued for alert.");
        }
        if (event.getCandidateStrength() != CandidateStrength.HIGH
                && event.getCandidateStrength() != CandidateStrength.MEDIUM) {
            return new AlertEligibility(false, "Candidate strength is not HIGH or MEDIUM.");
        }
        if (event.getCandidateScore() <= 0) {
            return new AlertEligibility(false, "Candidate score is not positive.");
        }
        if (event.getManualReviewStatus() == ManualReviewStatus.IGNORED) {
            return new AlertEligibility(false, "Candidate was manually ignored.");
        }
        if (!isTrustedHost(event.getArticle().getUrl())) {
            return new AlertEligibility(false, "Source host is not trusted for alert queueing.");
        }
        if (!hasPublicTargetEvidence(event)) {
            return new AlertEligibility(false, "No public target ticker or CIK was resolved for this RSS candidate.");
        }
        if (!NewsTextPatterns.hasDealHeadlineCue(event.getArticle().getHeadline())) {
            return new AlertEligibility(false, "Headline does not contain explicit M&A deal terms.");
        }
        if (!NewsTextPatterns.hasStrongMaPhrase(
                event.getArticle().getHeadline(),
                event.getArticle().getArticleText(),
                event.getMatchedPositiveKeywords()
        )) {
            return new AlertEligibility(false, "No explicit M&A deal phrase was found for alert eligibility.");
        }

        CandidateReviewInsightService.ReviewInsight reviewInsight = reviewInsightService.insight(event.getArticle(), event);
        DealTermsExtractionService.DealTerms dealTerms = dealTermsExtractionService.extract(event.getArticle(), event, reviewInsight);
        DealRelevanceService.RelevanceInsight relevanceInsight = dealRelevanceService.assess(
                event.getArticle(),
                event,
                reviewInsight,
                dealTerms
        );
        DealStageDetectionService.StageInsight stageInsight = dealStageDetectionService.detect(
                event.getArticle(),
                event,
                dealTerms,
                reviewInsight,
                relevanceInsight
        );

        if (reviewInsight.reviewVerdict() == ReviewVerdict.LIKELY_NOISE) {
            return withStrategy(false, "Review verdict is LIKELY_NOISE.", relevanceInsight, stageInsight);
        }
        if (relevanceInsight.dealRelevance() != DealRelevance.PUBLIC_TAKE_PRIVATE
                && relevanceInsight.dealRelevance() != DealRelevance.PUBLIC_CASH_ACQUISITION
                && relevanceInsight.dealRelevance() != DealRelevance.PUBLIC_PUBLIC_MERGER) {
            String reason = "Deal relevance is " + relevanceInsight.dealRelevance()
                    + ", not a public target strategy alert.";
            if (!relevanceInsight.relevanceWarnings().isEmpty()) {
                reason += " Warnings: " + String.join(", ", relevanceInsight.relevanceWarnings()) + ".";
            }
            return withStrategy(false, reason, relevanceInsight, stageInsight);
        }
        if ((relevanceInsight.dealRelevance() == DealRelevance.PUBLIC_TAKE_PRIVATE
                || relevanceInsight.dealRelevance() == DealRelevance.PUBLIC_CASH_ACQUISITION)
                && !NewsTextPatterns.hasCashOrFixedDealTerms(
                event.getArticle().getHeadline(),
                event.getArticle().getArticleText(),
                event.getMatchedPositiveKeywords()
        )) {
            return withStrategy(false, "No cash or fixed-price deal terms were found.", relevanceInsight, stageInsight);
        }
        if (relevanceInsight.tradability() != Tradability.HIGH && relevanceInsight.tradability() != Tradability.MEDIUM) {
            return withStrategy(false, "Tradability is " + relevanceInsight.tradability() + ".", relevanceInsight, stageInsight);
        }
        if (stageInsight.dealTiming() != DealTiming.EARLY && stageInsight.dealTiming() != DealTiming.MID_STAGE) {
            return withStrategy(false, "Deal timing is " + stageInsight.dealTiming() + ".", relevanceInsight, stageInsight);
        }
        if (stageInsight.dealTiming() == DealTiming.LATE_STAGE
                || stageInsight.dealTiming() == DealTiming.POST_CLOSE
                || stageInsight.dealTiming() == DealTiming.NOISE) {
            return withStrategy(false, "Deal timing is " + stageInsight.dealTiming() + ".", relevanceInsight, stageInsight);
        }
        if (stageInsight.dealStage() == DealStage.SHAREHOLDER_APPROVAL
                || stageInsight.dealStage() == DealStage.REGULATORY_APPROVAL
                || stageInsight.dealStage() == DealStage.CLOSING_EXPECTED
                || stageInsight.dealStage() == DealStage.COMPLETION_OR_CLOSED
                || stageInsight.dealStage() == DealStage.LITIGATION_OR_LAW_FIRM_UPDATE) {
            return withStrategy(false, "Deal stage is " + stageInsight.dealStage() + ".", relevanceInsight, stageInsight);
        }
        return withStrategy(true, "Strategy-eligible public target candidate.", relevanceInsight, stageInsight);
    }

    private boolean hasPublicTargetEvidence(DetectedEventEntity event) {
        return event.getTargetTicker() != null
                && !event.getTargetTicker().isBlank()
                && !"UNKNOWN".equalsIgnoreCase(event.getTargetTicker())
                || event.getTargetCik() != null
                && !event.getTargetCik().isBlank();
    }

    private AlertEligibility withStrategy(
            boolean eligible,
            String reason,
            DealRelevanceService.RelevanceInsight relevanceInsight,
            DealStageDetectionService.StageInsight stageInsight
    ) {
        return new AlertEligibility(
                eligible,
                reason,
                relevanceInsight.dealRelevance(),
                relevanceInsight.tradability(),
                stageInsight.dealStage(),
                stageInsight.dealTiming()
        );
    }

    private boolean isTrustedHost(String sourceUrl) {
        try {
            String host = URI.create(sourceUrl).getHost();
            if (host == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return TRUSTED_HOSTS.stream()
                    .anyMatch(trustedHost -> normalizedHost.equals(trustedHost) || normalizedHost.endsWith("." + trustedHost));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public record AlertEligibility(
            boolean eligible,
            String reason,
            DealRelevance dealRelevance,
            Tradability tradability,
            DealStage dealStage,
            DealTiming dealTiming
    ) {
        public AlertEligibility(boolean eligible, String reason) {
            this(eligible, reason, null, null, null, null);
        }
    }
}
