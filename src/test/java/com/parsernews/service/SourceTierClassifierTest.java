package com.parsernews.service;

import com.parsernews.persistence.SourceTier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceTierClassifierTest {
    private final SourceTierClassifier classifier = new SourceTierClassifier();

    @Test
    void globeNewswireMaIsPrimary() {
        assertThat(classifier.classify(
                "https://www.globenewswire.com/RssFeed/subjectcode/27-Mergers%20and%20Acquisitions/feedTitle/GlobeNewswire%20-%20Mergers%20and%20Acquisitions"
        )).isEqualTo(SourceTier.PRIMARY);
    }

    @Test
    void secPressReleasesIsPrimary() {
        assertThat(classifier.classify("https://www.sec.gov/news/pressreleases.rss"))
                .isEqualTo(SourceTier.PRIMARY);
    }

    @Test
    void prNewswireAcquisitionsMergersIsSecondary() {
        assertThat(classifier.classify(
                "https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/acquisitions-mergers-and-takeovers-list.rss"
        )).isEqualTo(SourceTier.SECONDARY);
    }

    @Test
    void globeNewswireFinancingIsSecondary() {
        assertThat(classifier.classify(
                "https://www.globenewswire.com/RssFeed/subjectcode/17-Financing%20Agreements/feedTitle/GlobeNewswire%20-%20Financing%20Agreements"
        )).isEqualTo(SourceTier.SECONDARY);
    }

    @Test
    void prNewswireShareholderActivismIsSecondary() {
        assertThat(classifier.classify(
                "https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/shareholder-activism-list.rss"
        )).isEqualTo(SourceTier.SECONDARY);
    }

    @Test
    void prNewswireAllNewsReleasesIsNoisy() {
        assertThat(classifier.classify(
                "https://www.prnewswire.com/rss/all-news-releases-from-PR-newswire-news.rss"
        )).isEqualTo(SourceTier.NOISY);
    }

    @Test
    void prNewswirePrivatePlacementIsNoisy() {
        assertThat(classifier.classify(
                "https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/private-placement-list.rss"
        )).isEqualTo(SourceTier.NOISY);
    }

    @Test
    void prNewswireStockOfferingIsNoisy() {
        assertThat(classifier.classify(
                "https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/stock-offering-list.rss"
        )).isEqualTo(SourceTier.NOISY);
    }

    @Test
    void globeNewswireCorporateActionIsNoisy() {
        assertThat(classifier.classify(
                "https://www.globenewswire.com/RssFeed/subjectcode/61-Corporate%20Action/feedTitle/GlobeNewswire%20-%20Corporate%20Action"
        )).isEqualTo(SourceTier.NOISY);
    }

    @Test
    void newsfilecorpIsBroad() {
        assertThat(classifier.classify("https://feeds.newsfilecorp.com/global/Last25Stories"))
                .isEqualTo(SourceTier.BROAD);
    }

    @Test
    void prNewswireBankruptcyIsBroad() {
        assertThat(classifier.classify(
                "https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/bankruptcy-list.rss"
        )).isEqualTo(SourceTier.BROAD);
    }

    @Test
    void nullAndBlankUrlReturnBroad() {
        assertThat(classifier.classify(null)).isEqualTo(SourceTier.BROAD);
        assertThat(classifier.classify("")).isEqualTo(SourceTier.BROAD);
        assertThat(classifier.classify("   ")).isEqualTo(SourceTier.BROAD);
    }
}
