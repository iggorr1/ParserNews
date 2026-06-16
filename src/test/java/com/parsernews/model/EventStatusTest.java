package com.parsernews.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventStatusTest {
    @Test
    void comparesStatusesBySeverity() {
        assertThat(EventStatus.IMPORTANT.isAtLeast(EventStatus.WATCHLIST)).isTrue();
        assertThat(EventStatus.MANUAL_REVIEW.isAtLeast(EventStatus.WATCHLIST)).isTrue();
        assertThat(EventStatus.IGNORED.isAtLeast(EventStatus.WATCHLIST)).isFalse();
    }
}
