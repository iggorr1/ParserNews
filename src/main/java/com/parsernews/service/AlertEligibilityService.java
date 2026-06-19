package com.parsernews.service;

import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Locale;

@Service
public class AlertEligibilityService {
    private static final List<String> TRUSTED_HOSTS = List.of(
            "globenewswire.com",
            "prnewswire.com",
            "sec.gov",
            "example.com"
    );

    public AlertEligibility evaluate(
            CandidateStrength candidateStrength,
            int candidateScore,
            String sourceUrl,
            boolean alreadyQueued
    ) {
        if (alreadyQueued) {
            return new AlertEligibility(false, "Candidate was already queued for alert.");
        }
        if (candidateStrength != CandidateStrength.HIGH) {
            return new AlertEligibility(false, "Candidate strength is not HIGH.");
        }
        if (candidateScore <= 0) {
            return new AlertEligibility(false, "Candidate score is not positive.");
        }
        if (!isTrustedHost(sourceUrl)) {
            return new AlertEligibility(false, "Source host is not trusted for alert queueing.");
        }
        return new AlertEligibility(true, "HIGH candidate from trusted source with positive score.");
    }

    public AlertEligibility evaluate(DetectedEventEntity event) {
        if (NewsTextPatterns.isRoundupAggregator(event.getArticle().getHeadline(), event.getArticle().getArticleText())) {
            return new AlertEligibility(false, "Roundup/aggregator article is not eligible for alert queueing.");
        }
        return evaluate(
                event.getCandidateStrength(),
                event.getCandidateScore(),
                event.getArticle().getUrl(),
                event.getAlertQueuedAt() != null
        );
    }

    private boolean isTrustedHost(String sourceUrl) {
        try {
            String host = URI.create(sourceUrl).getHost();
            if (host == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return TRUSTED_HOSTS.stream()
                    .anyMatch(trustedHost -> normalizedHost.equals(trustedHost) || normalizedHost.endsWith("." + trustedHost));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public record AlertEligibility(
            boolean eligible,
            String reason
    ) {
    }
}
