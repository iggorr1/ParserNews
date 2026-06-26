package com.parsernews.service;

import com.parsernews.persistence.SourceTier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Classifies RSS source URLs into quality tiers.
 *
 * PRIMARY  — dedicated M&A / regulatory feeds; nearly every item is relevant.
 * SECONDARY — topical feeds with high relevance but some noise.
 * BROAD    — broadly related (restructuring, bankruptcy, joint ventures).
 * NOISY    — high-noise feeds (offerings, placements, all-news aggregators);
 *             signals are capped at MEDIUM candidate strength.
 */
@Service
public class SourceTierClassifier {

    private static final List<String> PRIMARY_PATTERNS = List.of(
            "subjectcode/27",          // GlobeNewswire – Mergers & Acquisitions
            "sec.gov/news/pressreleases"
    );

    private static final List<String> SECONDARY_PATTERNS = List.of(
            "subjectcode/17",          // GlobeNewswire – Financing Agreements
            "acquisitions-mergers",    // PR Newswire M&A
            "shareholder-activism"     // PR Newswire shareholder activism
    );

    private static final List<String> NOISY_PATTERNS = List.of(
            "all-news-releases",
            "private-placement",
            "stock-offering",
            "financing-agreements",
            "venture-capital",
            "banking-financial-services",
            "contracts-list",
            "corporate-expansion",
            "subjectcode/57",          // GlobeNewswire – Changes In Share Capital
            "subjectcode/61",          // GlobeNewswire – Corporate Action
            "subjectcode/72"           // GlobeNewswire – Press Releases (generic)
    );

    public SourceTier classify(String url) {
        if (url == null || url.isBlank()) {
            return SourceTier.BROAD;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        for (String pattern : PRIMARY_PATTERNS) {
            if (lower.contains(pattern)) {
                return SourceTier.PRIMARY;
            }
        }
        for (String pattern : SECONDARY_PATTERNS) {
            if (lower.contains(pattern)) {
                return SourceTier.SECONDARY;
            }
        }
        for (String pattern : NOISY_PATTERNS) {
            if (lower.contains(pattern)) {
                return SourceTier.NOISY;
            }
        }
        return SourceTier.BROAD;
    }
}
