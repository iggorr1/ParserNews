package com.parsernews.service;

import com.parsernews.model.AnalysisResult;
import com.parsernews.model.EventStatus;
import com.parsernews.model.EventType;
import com.parsernews.model.NewsEvent;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsArticleRepository;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceRepository;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.DetectedEventRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventPersistenceServiceTest {
    @Test
    void saveTruncatesArticleTextBeforePersistence() {
        NewsSourceRepository sourceRepository = mock(NewsSourceRepository.class);
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        NewsSourceEntity source = new NewsSourceEntity("Test Source", NewsSourceType.RSS, "https://example.com/feed");
        AtomicReference<NewsArticleEntity> savedArticle = new AtomicReference<>();
        when(sourceRepository.findByName("Test Source")).thenReturn(Optional.of(source));
        when(articleRepository.findByUrlHash(any())).thenReturn(Optional.empty());
        when(articleRepository.save(any(NewsArticleEntity.class))).thenAnswer(invocation -> {
            NewsArticleEntity article = invocation.getArgument(0);
            savedArticle.set(article);
            return article;
        });
        when(eventRepository.findByArticle(any())).thenReturn(Optional.empty());
        EventPersistenceService service = new EventPersistenceService(
                sourceRepository,
                articleRepository,
                eventRepository,
                new FalsePositiveFilter(),
                new CandidateScoringService(),
                new AlertEligibilityService(),
                mock(RssCompanyEnrichmentService.class)
        );
        String longBody = "A".repeat(EventPersistenceService.MAX_ARTICLE_TEXT_LENGTH + 500);
        NewsEvent event = new NewsEvent(
                "TEST",
                "Test Company",
                "Test Company announces general update",
                longBody,
                "Test Source",
                "https://example.com/news/long"
        );
        AnalysisResult result = new AnalysisResult(
                EventType.UNKNOWN,
                EventStatus.IGNORED,
                0,
                List.of(),
                List.of(),
                "No signal"
        );

        service.save(event, result);

        assertThat(savedArticle.get()).isNotNull();
        assertThat(savedArticle.get().getArticleText()).hasSize(EventPersistenceService.MAX_ARTICLE_TEXT_LENGTH);
    }
}
