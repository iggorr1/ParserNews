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

    @Column(length = 32)
    private String promptVersion;

    @Column(precision = 12, scale = 4)
    private java.math.BigDecimal offerPrice;

    @Column(length = 512)
    private String targetCompany;

    @Column(length = 512)
    private String acquirerCompany;

    @Column(length = 32)
    private String targetTicker;

    @Column(length = 32)
    private String acquirerTicker;

    @Column(length = 16)
    private String tickerConfidence;

    @Column(length = 24)
    private String priceStatus;

    @Column(precision = 12, scale = 4)
    private java.math.BigDecimal verifiedOfferPrice;

    @Column(length = 1024)
    private String priceQuote;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected DealGroupAiReviewEntity() {
    }

    public DealGroupAiReviewEntity(
            String groupKey,
            String model,
            String promptVersion,
            AiReviewVerdict verdict,
            AiReviewConfidence confidence,
            Boolean tradablePublicTarget,
            ManualReviewStatus suggestedReviewStatus,
            ManualReviewReason suggestedReviewReason,
            String reason,
            String riskFlags,
            String rawJson,
            java.math.BigDecimal offerPrice,
            String targetCompany,
            String acquirerCompany,
            String targetTicker,
            String acquirerTicker,
            String tickerConfidence,
            String priceStatus,
            java.math.BigDecimal verifiedOfferPrice,
            String priceQuote
    ) {
        this.groupKey = groupKey;
        this.model = model;
        this.promptVersion = promptVersion;
        this.verdict = verdict;
        this.confidence = confidence;
        this.tradablePublicTarget = tradablePublicTarget;
        this.suggestedReviewStatus = suggestedReviewStatus;
        this.suggestedReviewReason = suggestedReviewReason;
        this.reason = truncate(reason, 4000);
        this.riskFlags = truncate(riskFlags, 2048);
        this.rawJson = truncate(rawJson, 10000);
        this.offerPrice = offerPrice;
        this.targetCompany = truncate(targetCompany, 512);
        this.acquirerCompany = truncate(acquirerCompany, 512);
        this.targetTicker = truncate(targetTicker, 32);
        this.acquirerTicker = truncate(acquirerTicker, 32);
        this.tickerConfidence = truncate(tickerConfidence, 16);
        this.priceStatus = truncate(priceStatus, 24);
        this.verifiedOfferPrice = verifiedOfferPrice;
        this.priceQuote = truncate(priceQuote, 1024);
    }

    public java.math.BigDecimal getOfferPrice() {
        return offerPrice;
    }

    public String getTargetCompany() {
        return targetCompany;
    }

    public String getAcquirerCompany() {
        return acquirerCompany;
    }

    public String getTargetTicker() {
        return targetTicker;
    }

    public String getAcquirerTicker() {
        return acquirerTicker;
    }

    public String getTickerConfidence() {
        return tickerConfidence;
    }

    public String getPriceStatus() {
        return priceStatus;
    }

    public java.math.BigDecimal getVerifiedOfferPrice() {
        return verifiedOfferPrice;
    }

    public String getPriceQuote() {
        return priceQuote;
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

    public String getPromptVersion() {
        return promptVersion;
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
