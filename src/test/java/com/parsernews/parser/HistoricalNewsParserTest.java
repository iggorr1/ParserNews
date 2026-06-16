package com.parsernews.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.model.EventStatus;
import com.parsernews.model.EventType;
import com.parsernews.model.NewsEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalNewsParserTest {
    private final MockNewsParser parser = new MockNewsParser(
            new ObjectMapper(),
            new DefaultResourceLoader(),
            "file:data/historical-news.json"
    );

    @Test
    void readsHistoricalNewsWithExpectedLabels() {
        List<NewsEvent> events = parser.readNews();

        assertThat(events).hasSize(3);
        assertThat(events.getFirst().expectedEventType()).isEqualTo(EventType.TAKE_PRIVATE_CONFIRMED);
        assertThat(events.getFirst().expectedStatus()).isEqualTo(EventStatus.IMPORTANT);
        assertThat(events.getFirst().notes()).isNotBlank();
    }
}
