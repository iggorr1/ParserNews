package com.parsernews.service;

import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.util.ArticleTextCleaner;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;

@Service
public class AlertMessageFormatter {
    private static final int SNIPPET_LIMIT = 280;

    public String format(DetectedEventEntity event) {
        NewsArticleEntity article = event.getArticle();
        return String.join(System.lineSeparator(),
                "M&A candidate alert preview",
                "Title: " + safe(article.getHeadline()),
                "Source: " + safe(article.getSource().getName()),
                "Host: " + safe(extractHost(article.getUrl())),
                "Strength: " + event.getCandidateStrength(),
                "Score: " + event.getCandidateScore(),
                "Reason: " + safe(event.getCandidateReason()),
                "Published at: " + formatInstant(article.getPublishedAt()),
                "Discovered at: " + formatInstant(article.getCollectedAt()),
                "Snippet: " + snippet(article.getArticleText(), article.getHeadline()),
                "URL: " + safe(article.getUrl())
        );
    }

    private String snippet(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return "N/A";
        }
        String normalized = ArticleTextCleaner.cleanTextForSnippet(text, fallback).replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "N/A";
        }
        if (normalized.length() <= SNIPPET_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, SNIPPET_LIMIT - 3) + "...";
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "N/A" : instant.toString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private String extractHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
