package com.parsernews.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    public Instant getProcessedAt() {
        return processedAt;
    }
}
