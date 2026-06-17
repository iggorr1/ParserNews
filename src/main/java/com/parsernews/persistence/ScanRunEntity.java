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
@Table(name = "scan_runs")
public class ScanRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private ScanRunTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private ScanRunStatus status;

    private int totalFetched;

    private int candidatesFound;

    private int savedArticles;

    private int duplicatesSkipped;

    private int errorsCount;

    @Column(length = 2048)
    private String errorMessage;

    protected ScanRunEntity() {
    }

    public ScanRunEntity(ScanRunTriggerType triggerType) {
        this.startedAt = Instant.now();
        this.triggerType = triggerType;
        this.status = ScanRunStatus.SUCCESS;
    }

    public void markSuccess(
            int totalFetched,
            int candidatesFound,
            int savedArticles,
            int duplicatesSkipped
    ) {
        this.finishedAt = Instant.now();
        this.status = ScanRunStatus.SUCCESS;
        this.totalFetched = totalFetched;
        this.candidatesFound = candidatesFound;
        this.savedArticles = savedArticles;
        this.duplicatesSkipped = duplicatesSkipped;
        this.errorsCount = 0;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.finishedAt = Instant.now();
        this.status = ScanRunStatus.FAILED;
        this.errorsCount = 1;
        this.errorMessage = truncate(errorMessage);
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

    public ScanRunTriggerType getTriggerType() {
        return triggerType;
    }

    public ScanRunStatus getStatus() {
        return status;
    }

    public int getTotalFetched() {
        return totalFetched;
    }

    public int getCandidatesFound() {
        return candidatesFound;
    }

    public int getSavedArticles() {
        return savedArticles;
    }

    public int getDuplicatesSkipped() {
        return duplicatesSkipped;
    }

    public int getErrorsCount() {
        return errorsCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2048 ? value : value.substring(0, 2048);
    }
}
