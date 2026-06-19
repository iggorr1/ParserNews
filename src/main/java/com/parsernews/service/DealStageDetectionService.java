package com.parsernews.service;

import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealStage;
import com.parsernews.model.DealStatus;
import com.parsernews.model.DealTiming;
import com.parsernews.model.ReviewVerdict;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.NewsArticleEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DealStageDetectionService {
    public StageInsight detect(
            NewsArticleEntity article,
            DetectedEventEntity event,
            DealTermsExtractionService.DealTerms dealTerms,
            CandidateReviewInsightService.ReviewInsight reviewInsight,
            DealRelevanceService.RelevanceInsight relevanceInsight
    ) {
        String lower = combinedText(article, event, dealTerms, reviewInsight, relevanceInsight)
                .toLowerCase(Locale.ROOT);
        List<String> warnings = new ArrayList<>();
        List<String> positives = new ArrayList<>();

        if (NewsTextPatterns.isRoundupAggregator(article.getHeadline(), article.getArticleText())) {
            warnings.add(NewsTextPatterns.ROUNDUP_AGGREGATOR_WARNING);
            return new StageInsight(
                    DealStage.UNKNOWN,
                    DealTiming.UNKNOWN,
                    "Roundup/aggregator article is not a primary deal timing source.",
                    warnings,
                    positives
            );
        }
        if (isLawFirm(lower, reviewInsight, relevanceInsight)) {
            warnings.add("law firm/shareholder alert");
            return new StageInsight(
                    DealStage.LITIGATION_OR_LAW_FIRM_UPDATE,
                    DealTiming.NOISE,
                    "Looks like a law firm or shareholder litigation update, not primary deal timing.",
                    warnings,
                    positives
            );
        }
        if (containsAny(lower, "completed acquisition", "completes acquisition", "completed the acquisition",
                "closed the transaction", "transaction closed", "completion of the acquisition",
                "completion of the merger", "completed merger", "merger completed")) {
            warnings.add("post-close update");
            warnings.add("not initial announcement");
            return new StageInsight(
                    DealStage.COMPLETION_OR_CLOSED,
                    DealTiming.POST_CLOSE,
                    "Deal appears completed or closed; this is after the main tradable announcement window.",
                    warnings,
                positives
            );
        }
        if (containsReverseTakeover(lower)) {
            warnings.add("reverse takeover / RTO");
            if (containsAny(lower, "non-binding letter of intent", "non-binding loi", "letter of intent")) {
                warnings.add("non-binding LOI");
            }
            if (containsAny(lower, "subject to entering into definitive agreement", "definitive agreement expected",
                    "definitive agreement is expected", "will enter into definitive agreement",
                    "expected to enter into a definitive agreement", "execution of the definitive agreement")) {
                warnings.add("definitive agreement not signed");
            }
            if (containsAny(lower, "trading has been halted", "trading halted", "trading halt", "has been halted", "halted pending")) {
                warnings.add("trading halted");
            }
            if (containsApprovalRequired(lower)) {
                warnings.add("shareholder/regulatory approvals required");
            }
            positives.add("proposed reverse takeover");
            return new StageInsight(
                    containsAny(lower, "non-binding letter of intent", "non-binding loi")
                            ? DealStage.RUMOR_OR_EXPLORATION
                            : DealStage.INITIAL_ANNOUNCEMENT,
                    DealTiming.EARLY,
                    "Proposed reverse takeover/RTO appears early and subject to later agreements or approvals.",
                    warnings,
                    positives
            );
        }
        if (isActualShareholderApproval(lower)) {
            warnings.add("not initial announcement");
            warnings.add("shareholder approval update");
            return new StageInsight(
                    DealStage.SHAREHOLDER_APPROVAL,
                    DealTiming.LATE_STAGE,
                "Shareholder approval suggests this is a late-stage deal update.",
                warnings,
                positives
            );
        }
        if (isActualRegulatoryApproval(lower)) {
            warnings.add("not initial announcement");
            warnings.add("regulatory approval update");
            return new StageInsight(
                    DealStage.REGULATORY_APPROVAL,
                    DealTiming.LATE_STAGE,
                    "Regulatory approval language suggests this is a late-stage deal update.",
                    warnings,
                    positives
            );
        }
        if (containsAny(lower, "expected to close", "closing expected", "expected effective date",
                "expected to be completed", "anticipated closing")) {
            warnings.add("late-stage closing language");
            return new StageInsight(
                    DealStage.CLOSING_EXPECTED,
                    DealTiming.LATE_STAGE,
                    "Closing timing language suggests the deal is already well underway.",
                    warnings,
                    positives
            );
        }
        if (containsAny(lower, "raises offer", "increases offer", "improved proposal", "revised proposal")) {
            positives.add("offer increase");
            return new StageInsight(
                    DealStage.OFFER_INCREASE,
                    lower.contains("revised proposal") || lower.contains("improved proposal") ? DealTiming.MID_STAGE : DealTiming.EARLY,
                    "Offer increase or revised proposal can be useful because terms changed publicly.",
                    warnings,
                positives
            );
        }
        if (containsAny(lower, "rumor", "exploring sale", "strategic alternatives", "considering sale")) {
            warnings.add("low-confidence early signal");
            return new StageInsight(
                    DealStage.RUMOR_OR_EXPLORATION,
                    DealTiming.EARLY,
                    "Early rumor or exploration language; useful only with manual review.",
                    warnings,
                    positives
            );
        }
        if (containsAny(lower, "enters into definitive agreement", "entered into definitive agreement",
                "announces acquisition", "announce acquisition", "to be acquired by", "will acquire")) {
            positives.add("initial announcement language");
            return new StageInsight(
                    DealStage.INITIAL_ANNOUNCEMENT,
                    DealTiming.EARLY,
                    "Looks like an initial public announcement rather than an approval or closing update.",
                    warnings,
                    positives
            );
        }
        if (dealTerms != null && (dealTerms.dealStatus() == DealStatus.DEFINITIVE_AGREEMENT
                || dealTerms.dealStatus() == DealStatus.MERGER_AGREEMENT)
                || containsAny(lower, "definitive agreement", "definitive merger agreement", "merger agreement")) {
            positives.add("definitive agreement");
            return new StageInsight(
                    DealStage.DEFINITIVE_AGREEMENT,
                    DealTiming.EARLY,
                    "Definitive agreement language usually belongs near the initial deal announcement.",
                    warnings,
                    positives
            );
        }
        return new StageInsight(
                DealStage.UNKNOWN,
                DealTiming.UNKNOWN,
                "Not enough deterministic timing language to classify deal stage.",
                warnings,
                positives
        );
    }

    private boolean isLawFirm(
            String lower,
            CandidateReviewInsightService.ReviewInsight reviewInsight,
            DealRelevanceService.RelevanceInsight relevanceInsight
    ) {
        return (reviewInsight != null && reviewInsight.reviewVerdict() == ReviewVerdict.LAW_FIRM_ALERT)
                || (relevanceInsight != null && relevanceInsight.dealRelevance() == DealRelevance.LAW_FIRM_OR_SHAREHOLDER_ALERT)
                || containsAny(lower, "shareholder alert", "stockholder alert", "law firm", "class action", "investigation");
    }

    private boolean isActualShareholderApproval(String lower) {
        return containsAny(lower, "shareholders approved", "stockholders approved",
                "approved by shareholders", "approved by stockholders",
                "received shareholder approval", "received stockholder approval",
                "announces shareholder approval", "announces stockholder approval");
    }

    private boolean isActualRegulatoryApproval(String lower) {
        return containsAny(lower, "received regulatory approval", "received regulatory approvals",
                "obtained regulatory approval", "obtained regulatory approvals",
                "regulatory approval received", "regulatory approvals received",
                "received all required regulatory", "approval from regulators");
    }

    private boolean containsApprovalRequired(String lower) {
        return containsAny(lower, "subject to shareholder approval", "requires shareholder approval",
                "subject to stockholder approval", "requires stockholder approval",
                "approval of the shareholders", "approval of shareholders",
                "subject to regulatory approval", "subject to regulatory approvals",
                "pending regulatory approval", "pending regulatory approvals",
                "requires regulatory approval", "requires regulatory approvals",
                "customary closing conditions, including regulatory approvals", "approval required");
    }

    private boolean containsReverseTakeover(String lower) {
        return containsAny(lower, "reverse takeover", "resulting issuer", "policy 5.2")
                || lower.matches(".*\\brto\\b.*");
    }

    private String combinedText(
            NewsArticleEntity article,
            DetectedEventEntity event,
            DealTermsExtractionService.DealTerms dealTerms,
            CandidateReviewInsightService.ReviewInsight reviewInsight,
            DealRelevanceService.RelevanceInsight relevanceInsight
    ) {
        List<String> values = new ArrayList<>();
        values.add(article.getHeadline());
        values.add(article.getArticleText());
        if (event != null) {
            values.add(event.getMatchedPositiveKeywords());
            values.add(event.getMatchedNegativeKeywords());
            values.add(event.getCandidateReason());
            values.add(event.getExplanation());
            values.add(event.getFalsePositiveReasons());
        }
        if (dealTerms != null) {
            values.add(dealTerms.dealStatus().name());
            values.add(dealTerms.dealSummary());
            values.add(String.join(" ", dealTerms.dealWarnings()));
        }
        if (reviewInsight != null) {
            values.add(reviewInsight.reviewVerdict().name());
            values.add(reviewInsight.reviewSummary());
            values.add(String.join(" ", reviewInsight.reviewRiskFlags()));
            values.add(String.join(" ", reviewInsight.reviewPositiveSignals()));
        }
        if (relevanceInsight != null) {
            values.add(relevanceInsight.dealRelevance().name());
            values.add(relevanceInsight.tradability().name());
            values.add(relevanceInsight.relevanceSummary());
            values.add(String.join(" ", relevanceInsight.relevanceWarnings()));
            values.add(String.join(" ", relevanceInsight.relevancePositiveSignals()));
        }
        return String.join(" ", values.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }

    private boolean containsAny(String lower, String... phrases) {
        for (String phrase : phrases) {
            if (lower.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    public record StageInsight(
            DealStage dealStage,
            DealTiming dealTiming,
            String stageSummary,
            List<String> stageWarnings,
            List<String> stagePositiveSignals
    ) {
    }
}
