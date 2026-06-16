package com.parsernews.service;

import com.parsernews.model.NewsEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateNewsFilterTest {
    private final DuplicateNewsFilter filter = new DuplicateNewsFilter();

    @Test
    void detectsDuplicateBySourceUrl() {
        NewsEvent first = news("AAA", "First headline", "https://example.com/same");
        NewsEvent second = news("BBB", "Different headline", "https://example.com/same");

        assertThat(filter.isDuplicate(first)).isFalse();
        assertThat(filter.isDuplicate(second)).isTrue();
    }

    @Test
    void detectsDuplicateByTickerAndHeadlineWhenUrlIsMissing() {
        NewsEvent first = news("AAA", "Same headline", "");
        NewsEvent second = news("AAA", "Same headline", "");

        assertThat(filter.isDuplicate(first)).isFalse();
        assertThat(filter.isDuplicate(second)).isTrue();
    }

    private NewsEvent news(String ticker, String headline, String sourceUrl) {
        return new NewsEvent(ticker, "Test Company", headline, "Body", "Test Source", sourceUrl);
    }
}
