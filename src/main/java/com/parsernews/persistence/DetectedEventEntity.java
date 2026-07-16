package com.parsernews.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "detected_events")
public class DetectedEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false, unique = true)
    private NewsArticleEntity article;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64, columnDefinition = "varchar(64)")
    private DetectedEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64, columnDefinition = "varchar(64)")
    private ReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 64, columnDefinition = "varchar(64)")
    private ValidationStatus validationStatus = ValidationStatus.UNREVIEWED;

    @Column(nullable = false)
    private int confidenceScore;

    private String targetCompany;

    @Column(length = 32)
    private String targetTicker;

    @Column(length = 16)
    private String targetCik;

    private Boolean targetPublicCompany = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, columnDefinition = "varchar(32)")
    private CompanyMatchConfidence targetMatchConfidence = CompanyMatchConfidence.NONE;

    private String acquirer;

    @Column(length = 32)
    private String buyerTicker;

    @Column(length = 16)
    private String buyerCik;

    private Boolean buyerPublicCompany = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, columnDefinition = "varchar(32)")
    private CompanyMatchConfidence buyerMatchConfidence = CompanyMatchConfidence.NONE;

    @Column(length = 1024)
    private String companyEnrichmentWarnings;

    private String offerPrice;

    private String transactionValue;

    private String cashOrStock;

    private String premiumPercent;

    private Integer candidateScore = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, columnDefinition = "varchar(32)")
    private CandidateStrength candidateStrength = CandidateStrength.NONE;

    @Column(length = 1024)
    private String candidateReason;

    private Boolean alertEligible = false;

    private Instant alertQueuedAt;

    @Column(length = 1024)
    private String alertReason;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, columnDefinition = "varchar(32)")
    private ManualReviewStatus manualReviewStatus = ManualReviewStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 64, columnDefinition = "varchar(64)")
    private ManualReviewReason manualReviewReason;

    @Column(length = 2048)
    private String manualReviewNote;

    private Instant manualReviewedAt;

    @Column(length = 2048)
    private String matchedPositiveKeywords;

    @Column(length = 2048)
    private String matchedNegativeKeywords;

    @Column(length = 2048)
    private String falsePositiveReasons;

    @Column(length = 4096)
    private String explanation;

    @Column(length = 2048)
    private String reviewNotes;

    @Column(nullable = false)
    private Instant detectedAt = Instant.now();

    protected DetectedEventEntity() {
    }

    public DetectedEventEntity(
            NewsArticleEntity article,
            DetectedEventType eventType,
            ReviewStatus reviewStatus,
            int confidenceScore,
            String targetCompany,
            String targetTicker,
            String acquirer,
            String offerPrice,
            String cashOrStock,
            String premiumPercent,
            int candidateScore,
            CandidateStrength candidateStrength,
            String candidateReason,
            boolean alertEligible,
            String alertReason,
            String matchedPositiveKeywords,
            String matchedNegativeKeywords,
            String falsePositiveReasons,
            String explanation
    ) {
        this.article = article;
        this.eventType = eventType;
        this.reviewStatus = reviewStatus;
        this.confidenceScore = confidenceScore;
        this.targetCompany = targetCompany;
        this.targetTicker = targetTicker;
        this.acquirer = acquirer;
        this.offerPrice = offerPrice;
        this.cashOrStock = cashOrStock;
        this.premiumPercent = premiumPercent;
        this.candidateScore = candidateScore;
        this.candidateStrength = candidateStrength == null ? CandidateStrength.NONE : candidateStrength;
        this.candidateReason = candidateReason;
        this.alertEligible = alertEligible;
        this.alertReason = alertReason;
        this.matchedPositiveKeywords = matchedPositiveKeywords;
        this.matchedNegativeKeywords = matchedNegativeKeywords;
        this.falsePositiveReasons = falsePositiveReasons;
        this.explanation = explanation;
    }

    public Long getId() {
        return id;
    }

    public NewsArticleEntity getArticle() {
        return article;
    }

    public DetectedEventType getEventType() {
        return eventType;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus == null ? ValidationStatus.UNREVIEWED : validationStatus;
    }

    public int getConfidenceScore() {
        return confidenceScore;
    }

    public String getTargetCompany() {
        return targetCompany;
    }

    public String getTargetTicker() {
        return targetTicker;
    }

    public String getTargetCik() {
        return targetCik;
    }

    public boolean isTargetPublicCompany() {
        return Boolean.TRUE.equals(targetPublicCompany);
    }

    public CompanyMatchConfidence getTargetMatchConfidence() {
        return targetMatchConfidence == null ? CompanyMatchConfidence.NONE : targetMatchConfidence;
    }

    public String getAcquirer() {
        return acquirer;
    }

    public String getBuyerTicker() {
        return buyerTicker;
    }

    public String getBuyerCik() {
        return buyerCik;
    }

    public boolean isBuyerPublicCompany() {
        return Boolean.TRUE.equals(buyerPublicCompany);
    }

    public CompanyMatchConfidence getBuyerMatchConfidence() {
        return buyerMatchConfidence == null ? CompanyMatchConfidence.NONE : buyerMatchConfidence;
    }

    public String getCompanyEnrichmentWarnings() {
        return companyEnrichmentWarnings;
    }

    public String getOfferPrice() {
        return offerPrice;
    }

    public String getTransactionValue() {
        return transactionValue;
    }

    public String getCashOrStock() {
        return cashOrStock;
    }

    public String getPremiumPercent() {
        return premiumPercent;
    }

    public int getCandidateScore() {
        return candidateScore == null ? 0 : candidateScore;
    }

    public CandidateStrength getCandidateStrength() {
        return candidateStrength == null ? CandidateStrength.NONE : candidateStrength;
    }

    public String getCandidateReason() {
        return candidateReason;
    }

    public boolean isAlertEligible() {
        return Boolean.TRUE.equals(alertEligible);
    }

    public Instant getAlertQueuedAt() {
        return alertQueuedAt;
    }

    public String getAlertReason() {
        return alertReason;
    }

    public ManualReviewStatus getManualReviewStatus() {
        return manualReviewStatus == null ? ManualReviewStatus.PENDING : manualReviewStatus;
    }

    public ManualReviewReason getManualReviewReason() {
        return manualReviewReason;
    }

    public String getManualReviewNote() {
        return manualReviewNote;
    }

    public Instant getManualReviewedAt() {
        return manualReviewedAt;
    }

    public String getMatchedPositiveKeywords() {
        return matchedPositiveKeywords;
    }

    public String getMatchedNegativeKeywords() {
        return matchedNegativeKeywords;
    }

    public String getFalsePositiveReasons() {
        return falsePositiveReasons;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void updateReview(String targetTicker, ValidationStatus validationStatus, String reviewNotes) {
        this.targetTicker = normalizeBlank(targetTicker);
        this.validationStatus = validationStatus == null ? ValidationStatus.UNREVIEWED : validationStatus;
        this.reviewNotes = normalizeBlank(reviewNotes);
    }

    public void markAlertQueued() {
        this.alertQueuedAt = Instant.now();
        this.alertEligible = false;
    }

    /**
     * Replaces the rule-derived analysis of a stored event after the rules themselves changed.
     * Deliberately leaves {@code manualReviewStatus}, {@code validationStatus}, {@code reviewNotes}
     * and alert/dispatch bookkeeping alone — those are human decisions and history, not rule output.
     */
    public void updateRuleAnalysis(
            DetectedEventType eventType,
            ReviewStatus reviewStatus,
            int confidenceScore,
            String targetTicker,
            String acquirer,
            String offerPrice,
            String cashOrStock,
            String premiumPercent,
            String matchedPositiveKeywords,
            String matchedNegativeKeywords,
            String falsePositiveReasons,
            String explanation
    ) {
        this.eventType = eventType;
        this.reviewStatus = reviewStatus;
        this.confidenceScore = confidenceScore;
        this.targetTicker = normalizeBlank(targetTicker);
        this.acquirer = normalizeBlank(acquirer);
        this.offerPrice = normalizeBlank(offerPrice);
        this.cashOrStock = normalizeBlank(cashOrStock);
        this.premiumPercent = normalizeBlank(premiumPercent);
        this.matchedPositiveKeywords = matchedPositiveKeywords;
        this.matchedNegativeKeywords = matchedNegativeKeywords;
        this.falsePositiveReasons = falsePositiveReasons;
        this.explanation = explanation;
    }

    public void updateCandidateScore(int candidateScore, CandidateStrength candidateStrength, String candidateReason) {
        this.candidateScore = candidateScore;
        this.candidateStrength = candidateStrength == null ? CandidateStrength.NONE : candidateStrength;
        this.candidateReason = candidateReason;
    }

    public void updateCompanyEnrichment(
            String targetTicker,
            String targetCik,
            boolean targetPublicCompany,
            CompanyMatchConfidence targetMatchConfidence,
            String buyerTicker,
            String buyerCik,
            boolean buyerPublicCompany,
            CompanyMatchConfidence buyerMatchConfidence,
            String companyEnrichmentWarnings
    ) {
        this.targetTicker = normalizeBlank(targetTicker);
        this.targetCik = normalizeBlank(targetCik);
        this.targetPublicCompany = targetPublicCompany;
        this.targetMatchConfidence = targetMatchConfidence == null ? CompanyMatchConfidence.NONE : targetMatchConfidence;
        this.buyerTicker = normalizeBlank(buyerTicker);
        this.buyerCik = normalizeBlank(buyerCik);
        this.buyerPublicCompany = buyerPublicCompany;
        this.buyerMatchConfidence = buyerMatchConfidence == null ? CompanyMatchConfidence.NONE : buyerMatchConfidence;
        this.companyEnrichmentWarnings = normalizeBlank(companyEnrichmentWarnings);
    }

    public void updateAlertEligibility(boolean alertEligible, String alertReason) {
        this.alertEligible = alertEligible;
        this.alertReason = alertReason;
    }

    public void updateManualReview(ManualReviewStatus manualReviewStatus, String manualReviewNote) {
        updateManualReview(manualReviewStatus, null, manualReviewNote);
    }

    public void updateManualReview(ManualReviewStatus manualReviewStatus, ManualReviewReason manualReviewReason, String manualReviewNote) {
        this.manualReviewStatus = manualReviewStatus == null ? ManualReviewStatus.PENDING : manualReviewStatus;
        this.manualReviewReason = this.manualReviewStatus == ManualReviewStatus.PENDING ? null : manualReviewReason;
        this.manualReviewNote = this.manualReviewStatus == ManualReviewStatus.PENDING ? null : normalizeBlank(manualReviewNote);
        this.manualReviewedAt = this.manualReviewStatus == ManualReviewStatus.PENDING ? null : Instant.now();
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
