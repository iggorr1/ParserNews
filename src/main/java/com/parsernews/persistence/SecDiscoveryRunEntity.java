package com.parsernews.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sec_discovery_runs")
public class SecDiscoveryRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Column(nullable = false, length = 32)
    private String status = "RUNNING";

    private int scannedCount;
    private int newCount;
    private int duplicateCount;
    private int createdOrUpdatedGroupCount;
    private int skippedCount;
    private int errorCount;

    @Column(length = 2048)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected SecDiscoveryRunEntity() {
    }

    public SecDiscoveryRunEntity(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Long getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getStatus() {
        return status;
    }

    public int getScannedCount() {
        return scannedCount;
    }

    public int getNewCount() {
        return newCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public int getCreatedOrUpdatedGroupCount() {
        return createdOrUpdatedGroupCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void finish(
            String status,
            int scannedCount,
            int newCount,
            int duplicateCount,
            int createdOrUpdatedGroupCount,
            int skippedCount,
            int errorCount,
            String errorMessage
    ) {
        this.finishedAt = Instant.now();
        this.status = status;
        this.scannedCount = scannedCount;
        this.newCount = newCount;
        this.duplicateCount = duplicateCount;
        this.createdOrUpdatedGroupCount = createdOrUpdatedGroupCount;
        this.skippedCount = skippedCount;
        this.errorCount = errorCount;
        this.errorMessage = truncate(errorMessage, 2048);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
