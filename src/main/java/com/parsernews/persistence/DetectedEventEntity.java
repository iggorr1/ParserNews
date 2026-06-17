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

    private String acquirer;

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

    public String getAcquirer() {
        return acquirer;
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

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
