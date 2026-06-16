package com.parsernews.parser;

import com.parsernews.model.NewsEvent;

import java.util.List;

public interface NewsSourceParser {
    List<NewsEvent> readNews();
}
