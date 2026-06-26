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
@Table(name = "deal_group_reviews")
public class DealGroupReviewEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 512)
    private String groupKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ManualReviewStatus manualReviewStatus = ManualReviewStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private ManualReviewReason manualReviewReason;

    @Column(length = 2048)
    private String manualReviewNote;

    private Instant manualReviewedAt;

    private Instant tgDispatchedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected DealGroupReviewEntity() {
    }

    public DealGroupReviewEntity(String groupKey) {
        this.groupKey = groupKey;
    }

    public Long getId() {
        return id;
    }

    public String getGroupKey() {
        return groupKey;
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

    public Instant getTgDispatchedAt() {
        return tgDispatchedAt;
    }

    public void markTgDispatched() {
        this.tgDispatchedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isTgDispatched() {
        return tgDispatchedAt != null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateManualReview(ManualReviewStatus status, ManualReviewReason reason, String note) {
        this.manualReviewStatus = status == null ? ManualReviewStatus.PENDING : status;
        this.manualReviewReason = this.manualReviewStatus == ManualReviewStatus.PENDING ? null : reason;
        this.manualReviewNote = this.manualReviewStatus == ManualReviewStatus.PENDING ? null : normalizeBlank(note);
        this.manualReviewedAt = this.manualReviewStatus == ManualReviewStatus.PENDING ? null : Instant.now();
        this.updatedAt = Instant.now();
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
