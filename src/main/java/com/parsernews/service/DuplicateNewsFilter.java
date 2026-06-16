package com.parsernews.service;

import com.parsernews.model.NewsEvent;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class DuplicateNewsFilter {
    private final Set<String> seenKeys = new HashSet<>();

    public boolean isDuplicate(NewsEvent event) {
        String key = buildKey(event);
        if (seenKeys.contains(key)) {
            return true;
        }
        seenKeys.add(key);
        return false;
    }

    private String buildKey(NewsEvent event) {
        if (event.sourceUrl() != null && !event.sourceUrl().isBlank()) {
            return normalize(event.sourceUrl());
        }
        return normalize(event.ticker() + "|" + event.headline());
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
