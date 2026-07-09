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
import java.time.LocalDate;

@Entity
@Table(name = "sec_filings")
public class SecFilingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String cik;

    @Column(nullable = false)
    private String companyName;

    @Column(length = 32)
    private String ticker;

    @Column(nullable = false, length = 32)
    private String form;

    private LocalDate filingDate;

    @Column(nullable = false, length = 64)
    private String accessionNumber;

    @Column(length = 512)
    private String primaryDocument;

    @Column(nullable = false, length = 2048)
    private String filingUrl;

    @Column(length = 64)
    private String signalType;

    @Column(length = 1024)
    private String signalReason;

    @Column(length = 2048)
    private String documentUrl;

    @Column(length = 4000)
    private String documentTextSnippet;

    private Instant documentFetchedAt;

    @Column(length = 32)
    private String documentFetchStatus;

    @Column(length = 32)
    private String documentSignalStrength;

    @Column(length = 1024)
    private String documentSignalReason;

    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private SecSignalType secSignalType = SecSignalType.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private SecSignalPriority secSignalPriority = SecSignalPriority.UNKNOWN;

    @Column(length = 1024)
    private String secSignalSummary;

    @Column(length = 1024)
    private String secSignalWarnings;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ManualReviewStatus manualReviewStatus = ManualReviewStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private ManualReviewReason manualReviewReason;

    @Column(length = 1024)
    private String manualReviewNote;

    private Instant manualReviewedAt;

    @Column(nullable = false)
    private boolean amendment = false;

    @Column(nullable = false)
    private Instant processedAt = Instant.now();

    protected SecFilingEntity() {
    }

    public SecFilingEntity(
            String cik,
            String companyName,
            String form,
            LocalDate filingDate,
            String accessionNumber,
            String primaryDocument,
            String filingUrl,
            String signalType,
            String signalReason
    ) {
        this.cik = cik;
        this.companyName = companyName;
        this.form = form;
        this.filingDate = filingDate;
        this.accessionNumber = accessionNumber;
        this.primaryDocument = primaryDocument;
        this.filingUrl = filingUrl;
        this.signalType = signalType;
        this.signalReason = signalReason;
    }

    public Long getId() {
        return id;
    }

    public String getCik() {
        return cik;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getForm() {
        return form;
    }

    public LocalDate getFilingDate() {
        return filingDate;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public String getPrimaryDocument() {
        return primaryDocument;
    }

    public String getFilingUrl() {
        return filingUrl;
    }

    public String getSignalType() {
        return signalType;
    }

    public String getSignalReason() {
        return signalReason;
    }

    public boolean isAmendment() {
        return amendment;
    }

    public void markAsAmendment() {
        this.amendment = true;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public String getDocumentTextSnippet() {
        return documentTextSnippet;
    }

    public Instant getDocumentFetchedAt() {
        return documentFetchedAt;
    }

    public String getDocumentFetchStatus() {
        return documentFetchStatus;
    }

    public String getDocumentSignalStrength() {
        return documentSignalStrength;
    }

    public String getDocumentSignalReason() {
        return documentSignalReason;
    }

    public SecSignalType getSecSignalType() {
        return secSignalType == null ? SecSignalType.UNKNOWN : secSignalType;
    }

    public SecSignalPriority getSecSignalPriority() {
        return secSignalPriority == null ? SecSignalPriority.UNKNOWN : secSignalPriority;
    }

    public String getSecSignalSummary() {
        return secSignalSummary;
    }

    public String getSecSignalWarnings() {
        return secSignalWarnings;
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

    public void markDocumentFetched(
            String documentUrl,
            String snippet,
            String signalStrength,
            String signalReason,
            SecSignalType secSignalType,
            SecSignalPriority secSignalPriority,
            String secSignalSummary,
            String secSignalWarnings
    ) {
        this.documentUrl = documentUrl;
        this.documentTextSnippet = snippet;
        this.documentFetchedAt = Instant.now();
        this.documentFetchStatus = "FETCHED";
        this.documentSignalStrength = signalStrength;
        this.documentSignalReason = signalReason;
        this.secSignalType = secSignalType == null ? SecSignalType.UNKNOWN : secSignalType;
        this.secSignalPriority = secSignalPriority == null ? SecSignalPriority.UNKNOWN : secSignalPriority;
        this.secSignalSummary = secSignalSummary;
        this.secSignalWarnings = secSignalWarnings;
    }

    public void updateSecSignal(
            SecSignalType secSignalType,
            SecSignalPriority secSignalPriority,
            String secSignalSummary,
            String secSignalWarnings
    ) {
        this.secSignalType = secSignalType == null ? SecSignalType.UNKNOWN : secSignalType;
        this.secSignalPriority = secSignalPriority == null ? SecSignalPriority.UNKNOWN : secSignalPriority;
        this.secSignalSummary = secSignalSummary;
        this.secSignalWarnings = secSignalWarnings;
    }

    public void markDocumentFetched(String documentUrl, String snippet, String signalStrength, String signalReason) {
        markDocumentFetched(
                documentUrl,
                snippet,
                signalStrength,
                signalReason,
                SecSignalType.UNKNOWN,
                SecSignalPriority.UNKNOWN,
                signalReason,
                null
        );
    }

    /** Lightweight: store fetched document text for downstream analysis without touching the signal. */
    public void attachDocumentText(String documentUrl, String snippet) {
        this.documentUrl = documentUrl;
        this.documentTextSnippet = snippet;
        this.documentFetchedAt = Instant.now();
        this.documentFetchStatus = "FETCHED";
    }

    public void markDocumentFetchFailed(String documentUrl, String signalReason) {
        this.documentUrl = documentUrl;
        this.documentFetchedAt = Instant.now();
        this.documentFetchStatus = "FAILED";
        this.documentSignalStrength = "NONE";
        this.documentSignalReason = signalReason;
        this.secSignalType = SecSignalType.UNKNOWN;
        this.secSignalPriority = SecSignalPriority.UNKNOWN;
        this.secSignalSummary = "Document could not be fetched for analysis.";
        this.secSignalWarnings = signalReason;
    }

    public void updateManualReview(ManualReviewStatus manualReviewStatus, ManualReviewReason manualReviewReason, String manualReviewNote) {
        this.manualReviewStatus = manualReviewStatus == null ? ManualReviewStatus.PENDING : manualReviewStatus;
        this.manualReviewReason = this.manualReviewStatus == ManualReviewStatus.PENDING ? null : manualReviewReason;
        this.manualReviewNote = this.manualReviewStatus == ManualReviewStatus.PENDING ? null : normalizeBlank(manualReviewNote);
        this.manualReviewedAt = this.manualReviewStatus == ManualReviewStatus.PENDING ? null : Instant.now();
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
