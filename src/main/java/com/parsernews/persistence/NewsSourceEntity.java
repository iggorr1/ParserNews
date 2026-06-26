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
@Table(name = "news_sources")
public class NewsSourceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NewsSourceType type;

    @Column(length = 2048)
    private String url;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SourceTier tier = SourceTier.BROAD;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected NewsSourceEntity() {
    }

    public NewsSourceEntity(String name, NewsSourceType type, String url) {
        this.name = name;
        this.type = type;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public NewsSourceType getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public SourceTier getTier() {
        return tier == null ? SourceTier.BROAD : tier;
    }

    public void setTier(SourceTier tier) {
        this.tier = tier == null ? SourceTier.BROAD : tier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
