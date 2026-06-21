package com.parsernews.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sec_watchlist_companies")
public class SecWatchlistCompanyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10, unique = true)
    private String cik;

    @Column(nullable = false, length = 255)
    private String companyName;

    @Column(length = 32)
    private String ticker;

    @Column(length = 1024)
    private String notes;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected SecWatchlistCompanyEntity() {
    }

    public SecWatchlistCompanyEntity(String cik, String companyName, String ticker, String notes, boolean enabled) {
        this.cik = cik;
        this.companyName = companyName;
        this.ticker = normalizeBlank(ticker);
        this.notes = normalizeBlank(notes);
        this.enabled = enabled;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
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

    public String getNotes() {
        return notes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String companyName, String ticker, String notes, Boolean enabled) {
        if (companyName != null && !companyName.isBlank()) {
            this.companyName = companyName.trim();
        }
        this.ticker = normalizeBlank(ticker);
        this.notes = normalizeBlank(notes);
        if (enabled != null) {
            this.enabled = enabled;
        }
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
