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
    @Column(nullable = false)
    private DetectedEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus reviewStatus;

    @Column(nullable = false)
    private int confidenceScore;

    private String targetCompany;

    @Column(length = 32)
    private String targetTicker;

    private String acquirer;

    private String offerPrice;

    private String transactionValue;

    private String cashOrStock;

    @Column(length = 2048)
    private String matchedPositiveKeywords;

    @Column(length = 2048)
    private String matchedNegativeKeywords;

    @Column(length = 2048)
    private String falsePositiveReasons;

    @Column(length = 4096)
    private String explanation;

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

    public Instant getDetectedAt() {
        return detectedAt;
    }
}
