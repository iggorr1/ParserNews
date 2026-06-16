package com.parsernews.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RulesConfigLoader {
    private final ObjectMapper objectMapper;

    public RulesConfigLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalyzerRules loadRules() {
        try {
            ClassPathResource resource = new ClassPathResource("analyzer-rules.json");
            return objectMapper.readValue(resource.getInputStream(), AnalyzerRules.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read analyzer rules from analyzer-rules.json", exception);
        }
    }
}
