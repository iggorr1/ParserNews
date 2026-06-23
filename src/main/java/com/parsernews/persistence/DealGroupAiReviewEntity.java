package com.parsernews.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "deal_group_ai_reviews")
public class DealGroupAiReviewEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String groupKey;

    @Column(nullable = false, length = 128)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AiReviewVerdict verdict;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiReviewConfidence confidence;

    private Boolean tradablePublicTarget;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ManualReviewStatus suggestedReviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private ManualReviewReason suggestedReviewReason;

    @Column(length = 4000)
    private String reason;

    @Column(length = 2048)
    private String riskFlags;

    @Column(length = 10000)
    private String rawJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected DealGroupAiReviewEntity() {
    }

    public DealGroupAiReviewEntity(
            String groupKey,
            String model,
            AiReviewVerdict verdict,
            AiReviewConfidence confidence,
            Boolean tradablePublicTarget,
            ManualReviewStatus suggestedReviewStatus,
            ManualReviewReason suggestedReviewReason,
            String reason,
            String riskFlags,
            String rawJson
    ) {
        this.groupKey = groupKey;
        this.model = model;
        this.verdict = verdict;
        this.confidence = confidence;
        this.tradablePublicTarget = tradablePublicTarget;
        this.suggestedReviewStatus = suggestedReviewStatus;
        this.suggestedReviewReason = suggestedReviewReason;
        this.reason = truncate(reason, 4000);
        this.riskFlags = truncate(riskFlags, 2048);
        this.rawJson = truncate(rawJson, 10000);
    }

    public Long getId() {
        return id;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public String getModel() {
        return model;
    }

    public AiReviewVerdict getVerdict() {
        return verdict;
    }

    public AiReviewConfidence getConfidence() {
        return confidence;
    }

    public Boolean getTradablePublicTarget() {
        return tradablePublicTarget;
    }

    public ManualReviewStatus getSuggestedReviewStatus() {
        return suggestedReviewStatus;
    }

    public ManualReviewReason getSuggestedReviewReason() {
        return suggestedReviewReason;
    }

    public String getReason() {
        return reason;
    }

    public String getRiskFlags() {
        return riskFlags;
    }

    public String getRawJson() {
        return rawJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
