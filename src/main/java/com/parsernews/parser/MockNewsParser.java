package com.parsernews.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.model.NewsEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class MockNewsParser implements NewsSourceParser {
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String newsFile;

    public MockNewsParser(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("${scanner.news-file:classpath:mock-news.json}") String newsFile
    ) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.newsFile = newsFile;
    }

    @Override
    public List<NewsEvent> readNews() {
        try {
            Resource resource = resourceLoader.getResource(newsFile);
            return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read news data from " + newsFile, exception);
        }
    }
}
