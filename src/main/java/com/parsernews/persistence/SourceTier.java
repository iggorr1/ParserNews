package com.parsernews.persistence;

public enum SourceTier {
    /**
     * Dedicated M&A / SEC feeds. Every item is expected to be relevant.
     * Example: GlobeNewswire M&A category, SEC press releases.
     */
    PRIMARY,

    /**
     * High-quality topical feeds with occasional noise.
     * Example: PR Newswire acquisitions-mergers, shareholder-activism.
     */
    SECONDARY,

    /**
     * Broadly related feeds — restructuring, bankruptcy, joint ventures.
     * Useful for discovery but expect significant non-M&A volume.
     */
    BROAD,

    /**
     * High-noise feeds — offerings, private placements, all-news.
     * Signals from these sources are capped at MEDIUM candidate strength
     * to prevent noisy articles from reaching alert-eligible status.
     */
    NOISY
}
