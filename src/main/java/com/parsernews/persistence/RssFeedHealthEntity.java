package com.parsernews.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "rss_feed_health")
public class RssFeedHealthEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 2048)
    private String feedUrl;

    private Instant lastSuccessAt;
    private Instant lastErrorAt;

    @Column(nullable = false)
    private int consecutiveErrors = 0;

    @Column(length = 1024)
    private String lastErrorMessage;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected RssFeedHealthEntity() {
    }

    public RssFeedHealthEntity(String feedUrl) {
        this.feedUrl = feedUrl;
    }

    public void recordSuccess() {
        this.lastSuccessAt = Instant.now();
        this.consecutiveErrors = 0;
    }

    public void recordError(String message) {
        this.lastErrorAt = Instant.now();
        this.consecutiveErrors++;
        this.lastErrorMessage = message == null ? null
                : message.length() > 1024 ? message.substring(0, 1024) : message;
    }

    public Long getId() { return id; }
    public String getFeedUrl() { return feedUrl; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public Instant getLastErrorAt() { return lastErrorAt; }
    public int getConsecutiveErrors() { return consecutiveErrors; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
