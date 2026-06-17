package com.parsernews.web;

import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsArticleRepository;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleControllerTest {
    @Test
    void returnsSavedArticlesInRepositoryOrder() {
        NewsArticleEntity latest = article(2L, "Latest merger article", "https://example.com/latest");
        NewsArticleEntity older = article(1L, "Older merger article", "https://example.com/older");
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(articleRepository.findTop200ByOrderByCollectedAtDesc()).thenReturn(List.of(latest, older));
        ArticleController controller = new ArticleController(articleRepository, eventRepository);

        List<ArticleController.ArticleListResponse> response = controller.articles(null, false, 200);

        assertThat(response).extracting(ArticleController.ArticleListResponse::id)
                .containsExactly(2L, 1L);
        assertThat(response.getFirst().title()).isEqualTo("Latest merger article");
        assertThat(response.getFirst().snippet()).contains("Shareholders will receive");
        assertThat(response.getFirst().candidateScore()).isZero();
        assertThat(response.getFirst().candidateStrength()).isEqualTo(CandidateStrength.NONE);
    }

    @Test
    void returnsOnlyCandidateArticles() {
        NewsArticleEntity candidateArticle = article(5L, "Target to be acquired", "https://example.com/candidate");
        DetectedEventEntity event = event(candidateArticle, 90, CandidateStrength.HIGH);
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(event));
        ArticleController controller = new ArticleController(articleRepository, eventRepository);

        List<ArticleController.ArticleListResponse> response = controller.candidateArticles(200);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(5L);
        assertThat(response.getFirst().candidate()).isTrue();
        assertThat(response.getFirst().eventType()).isEqualTo(DetectedEventType.DEFINITIVE_AGREEMENT);
        assertThat(response.getFirst().matchedPositiveKeywords()).contains("definitive agreement");
        assertThat(response.getFirst().candidateScore()).isEqualTo(90);
        assertThat(response.getFirst().candidateStrength()).isEqualTo(CandidateStrength.HIGH);
        assertThat(response.getFirst().candidateReason()).contains("HIGH");
    }

    @Test
    void candidateArticlesPreferStrongerCandidates() {
        NewsArticleEntity lowArticle = article(1L, "Target rumor", "https://example.com/low");
        NewsArticleEntity highArticle = article(2L, "Target merger agreement", "https://example.com/high");
        NewsArticleEntity noneArticle = article(3L, "Regular article", "https://example.com/none");
        DetectedEventEntity low = event(lowArticle, 30, CandidateStrength.LOW);
        DetectedEventEntity high = event(highArticle, 90, CandidateStrength.HIGH);
        DetectedEventEntity none = event(noneArticle, 0, CandidateStrength.NONE);
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(low, none, high));
        ArticleController controller = new ArticleController(articleRepository, eventRepository);

        List<ArticleController.ArticleListResponse> response = controller.candidateArticles(200);

        assertThat(response).extracting(ArticleController.ArticleListResponse::id)
                .containsExactly(2L, 1L);
    }

    @Test
    void returnsNotFoundForMissingArticleId() {
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(articleRepository.findById(404L)).thenReturn(Optional.empty());
        ArticleController controller = new ArticleController(articleRepository, eventRepository);

        assertThatThrownBy(() -> controller.article(404L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    private NewsArticleEntity article(Long id, String headline, String url) {
        NewsSourceEntity source = new NewsSourceEntity("Test Source", NewsSourceType.RSS, "https://example.com/feed");
        setId(source, id + 100);
        NewsArticleEntity article = new NewsArticleEntity(
                source,
                "hash-" + id,
                "TEST",
                "Test Company",
                headline,
                "Shareholders will receive $5.00 per share in cash.",
                url,
                Instant.parse("2026-06-17T08:00:00Z")
        );
        setId(article, id);
        return article;
    }

    private DetectedEventEntity event(NewsArticleEntity article, int candidateScore, CandidateStrength candidateStrength) {
        DetectedEventEntity event = new DetectedEventEntity(
                article,
                DetectedEventType.DEFINITIVE_AGREEMENT,
                ReviewStatus.HIGH_PRIORITY_SIGNAL,
                80,
                "Test Company",
                "TEST",
                "Buyer LLC",
                "$5.00",
                "CASH",
                "40%",
                candidateScore,
                candidateStrength,
                "Matched " + candidateStrength + " candidate signal: definitive agreement.",
                "definitive agreement|per share in cash",
                "",
                "",
                "Matched test candidate"
        );
        setId(event, 50L);
        return event;
    }

    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
