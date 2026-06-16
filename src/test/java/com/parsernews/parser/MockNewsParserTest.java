package com.parsernews.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.model.NewsEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockNewsParserTest {
    private final MockNewsParser parser = new MockNewsParser(
            new ObjectMapper(),
            new DefaultResourceLoader(),
            "classpath:mock-news.json"
    );

    @Test
    void readsMockNewsFromJsonResource() {
        List<NewsEvent> events = parser.readNews();

        assertThat(events).hasSize(6);
        assertThat(events)
                .extracting(NewsEvent::ticker)
                .containsExactly("ABCD", "MXYZ", "TEND", "DILU", "BANK", "ALT");
    }
}
