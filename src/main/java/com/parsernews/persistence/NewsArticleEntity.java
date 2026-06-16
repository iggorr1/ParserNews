package com.parsernews.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "news_articles")
public class NewsArticleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private NewsSourceEntity source;

    @Column(nullable = false, unique = true, length = 128)
    private String urlHash;

    @Column(length = 32)
    private String ticker;

    private String companyName;

    @Column(nullable = false, length = 2048)
    private String headline;

    @Column(length = 10000)
    private String articleText;

    @Column(nullable = false, length = 2048)
    private String url;

    private Instant publishedAt;

    @Column(nullable = false)
    private Instant collectedAt = Instant.now();

    protected NewsArticleEntity() {
    }

    public NewsArticleEntity(
            NewsSourceEntity source,
            String urlHash,
            String ticker,
            String companyName,
            String headline,
            String articleText,
            String url,
            Instant publishedAt
    ) {
        this.source = source;
        this.urlHash = urlHash;
        this.ticker = ticker;
        this.companyName = companyName;
        this.headline = headline;
        this.articleText = articleText;
        this.url = url;
        this.publishedAt = publishedAt;
    }

    public Long getId() {
        return id;
    }

    public NewsSourceEntity getSource() {
        return source;
    }

    public String getUrlHash() {
        return urlHash;
    }

    public String getTicker() {
        return ticker;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getHeadline() {
        return headline;
    }

    public String getArticleText() {
        return articleText;
    }

    public String getUrl() {
        return url;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }
}
