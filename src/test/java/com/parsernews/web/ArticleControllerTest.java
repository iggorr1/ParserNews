package com.parsernews.web;

import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsArticleRepository;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import com.parsernews.service.CandidateReviewInsightService;
import com.parsernews.service.DealRelevanceService;
import com.parsernews.service.DealStageDetectionService;
import com.parsernews.service.DealTermsExtractionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
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
        ArticleController controller = controller(articleRepository, eventRepository);

        List<ArticleController.ArticleListResponse> response = controller.articles(null, false, 200);

        assertThat(response).extracting(ArticleController.ArticleListResponse::id)
                .containsExactly(2L, 1L);
        assertThat(response.getFirst().title()).isEqualTo("Latest merger article");
        assertThat(response.getFirst().snippet()).contains("Shareholders will receive");
        assertThat(response.getFirst().candidateScore()).isZero();
        assertThat(response.getFirst().candidateStrength()).isEqualTo(CandidateStrength.NONE);
        assertThat(response.getFirst().manualReviewStatus()).isEqualTo(ManualReviewStatus.PENDING);
    }

    @Test
    void returnsOnlyCandidateArticles() {
        NewsArticleEntity candidateArticle = article(5L, "Target to be acquired", "https://example.com/candidate");
        DetectedEventEntity event = event(candidateArticle, 90, CandidateStrength.HIGH);
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(event));
        ArticleController controller = controller(articleRepository, eventRepository);

        List<ArticleController.ArticleListResponse> response = controller.candidateArticles(200);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(5L);
        assertThat(response.getFirst().candidate()).isTrue();
        assertThat(response.getFirst().eventType()).isEqualTo(DetectedEventType.DEFINITIVE_AGREEMENT);
        assertThat(response.getFirst().matchedPositiveKeywords()).contains("definitive agreement");
        assertThat(response.getFirst().candidateScore()).isEqualTo(90);
        assertThat(response.getFirst().candidateStrength()).isEqualTo(CandidateStrength.HIGH);
        assertThat(response.getFirst().candidateReason()).contains("HIGH");
        assertThat(response.getFirst().manualReviewStatus()).isEqualTo(ManualReviewStatus.PENDING);
        assertThat(response.getFirst().manualReviewReason()).isNull();
        assertThat(response.getFirst().offerPrice()).isEqualByComparingTo("5.00");
        assertThat(response.getFirst().paymentType()).isEqualTo(com.parsernews.model.PaymentType.CASH);
        assertThat(response.getFirst().tradability()).isNotNull();
        assertThat(response.getFirst().dealTiming()).isNotNull();
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
        ArticleController controller = controller(articleRepository, eventRepository);

        List<ArticleController.ArticleListResponse> response = controller.candidateArticles(200);

        assertThat(response).extracting(ArticleController.ArticleListResponse::id)
                .containsExactly(2L, 1L);
    }

    @Test
    void reviewedArticlesReturnOnlyReviewedCandidatesLatestFirst() {
        NewsArticleEntity usefulArticle = article(11L, "Useful candidate", "https://example.com/useful");
        NewsArticleEntity ignoredArticle = article(12L, "Ignored candidate", "https://example.com/ignored");
        NewsArticleEntity pendingArticle = article(13L, "Pending candidate", "https://example.com/pending");
        DetectedEventEntity useful = event(usefulArticle, 90, CandidateStrength.HIGH);
        DetectedEventEntity ignored = event(ignoredArticle, 80, CandidateStrength.HIGH);
        DetectedEventEntity pending = event(pendingArticle, 70, CandidateStrength.HIGH);
        useful.updateManualReview(ManualReviewStatus.USEFUL, ManualReviewReason.GOOD_SIGNAL, "Good");
        ignored.updateManualReview(ManualReviewStatus.IGNORED, ManualReviewReason.PRIVATE_COMPANY, "Private");
        setField(useful, "manualReviewedAt", Instant.parse("2026-06-17T08:00:00Z"));
        setField(ignored, "manualReviewedAt", Instant.parse("2026-06-17T09:00:00Z"));
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(eventRepository.findAll()).thenReturn(List.of(pending, useful, ignored));
        ArticleController controller = controller(articleRepository, eventRepository);

        List<ArticleController.ArticleListResponse> response = controller.reviewedArticles(null, null, 50);

        assertThat(response).extracting(ArticleController.ArticleListResponse::id)
                .containsExactly(12L, 11L);
        assertThat(response.getFirst().manualReviewStatus()).isEqualTo(ManualReviewStatus.IGNORED);
        assertThat(response.getFirst().manualReviewReason()).isEqualTo(ManualReviewReason.PRIVATE_COMPANY);
    }

    @Test
    void reviewedArticlesCanFilterByStatusAndReason() {
        NewsArticleEntity usefulArticle = article(14L, "Useful candidate", "https://example.com/useful-filter");
        NewsArticleEntity ignoredArticle = article(15L, "Ignored candidate", "https://example.com/ignored-filter");
        DetectedEventEntity useful = event(usefulArticle, 90, CandidateStrength.HIGH);
        DetectedEventEntity ignored = event(ignoredArticle, 80, CandidateStrength.HIGH);
        useful.updateManualReview(ManualReviewStatus.USEFUL, ManualReviewReason.GOOD_SIGNAL, "Good");
        ignored.updateManualReview(ManualReviewStatus.IGNORED, ManualReviewReason.PRIVATE_COMPANY, "Private");
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(eventRepository.findAll()).thenReturn(List.of(useful, ignored));
        ArticleController controller = controller(articleRepository, eventRepository);

        List<ArticleController.ArticleListResponse> response = controller.reviewedArticles(
                ManualReviewStatus.IGNORED,
                ManualReviewReason.PRIVATE_COMPANY,
                50
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(15L);
        assertThat(response.getFirst().manualReviewStatus()).isEqualTo(ManualReviewStatus.IGNORED);
        assertThat(response.getFirst().manualReviewReason()).isEqualTo(ManualReviewReason.PRIVATE_COMPANY);
    }

    @Test
    void candidateCsvExportReturnsCsvWithEscapedValues() {
        NewsArticleEntity article = article(
                16L,
                "Buyer agrees to acquire Target \"Alpha\", Inc.\nDeal",
                "Target Alpha enters into definitive agreement. Shareholders will receive $5.00 per share in cash.",
                "https://example.com/export-candidate"
        );
        DetectedEventEntity event = event(article, 90, CandidateStrength.HIGH);
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(eventRepository.findAll()).thenReturn(List.of(event));
        ArticleController controller = controller(articleRepository, eventRepository);

        ResponseEntity<String> response = controller.exportCandidateArticlesCsv(
                null,
                null,
                null,
                null,
                null,
                null,
                500
        );

        assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(response.getBody()).startsWith("id,title,url,source,host,publishedAt,candidateStrength,candidateScore");
        assertThat(response.getBody()).contains("\"Buyer agrees to acquire Target \"\"Alpha\"\", Inc.\nDeal\"");
        assertThat(response.getBody()).contains("HIGH");
        assertThat(response.getBody()).contains("PUBLIC_CASH_ACQUISITION");
    }

    @Test
    void reviewedCsvExportReturnsReviewedRowsAndSupportsFilters() {
        NewsArticleEntity usefulArticle = article(17L, "Useful export candidate", "https://example.com/useful-export");
        NewsArticleEntity ignoredArticle = article(18L, "Ignored export candidate", "https://example.com/ignored-export");
        NewsArticleEntity pendingArticle = article(19L, "Pending export candidate", "https://example.com/pending-export");
        DetectedEventEntity useful = event(usefulArticle, 90, CandidateStrength.HIGH);
        DetectedEventEntity ignored = event(ignoredArticle, 80, CandidateStrength.HIGH);
        DetectedEventEntity pending = event(pendingArticle, 70, CandidateStrength.HIGH);
        useful.updateManualReview(ManualReviewStatus.USEFUL, ManualReviewReason.GOOD_SIGNAL, "Good signal");
        ignored.updateManualReview(ManualReviewStatus.IGNORED, ManualReviewReason.PRIVATE_COMPANY, "Private company");
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(eventRepository.findAll()).thenReturn(List.of(useful, ignored, pending));
        ArticleController controller = controller(articleRepository, eventRepository);

        ResponseEntity<String> response = controller.exportReviewedArticlesCsv(
                ManualReviewStatus.IGNORED,
                ManualReviewReason.PRIVATE_COMPANY,
                null,
                null,
                null,
                null,
                null,
                null,
                500
        );

        assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(response.getBody()).contains("manualReviewStatus,manualReviewReason,manualReviewNote,manualReviewedAt");
        assertThat(response.getBody()).contains("Ignored export candidate");
        assertThat(response.getBody()).contains("PRIVATE_COMPANY");
        assertThat(response.getBody()).doesNotContain("Useful export candidate");
        assertThat(response.getBody()).doesNotContain("Pending export candidate");
    }

    @Test
    void returnsNotFoundForMissingArticleId() {
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(articleRepository.findById(404L)).thenReturn(Optional.empty());
        ArticleController controller = controller(articleRepository, eventRepository);

        assertThatThrownBy(() -> controller.article(404L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void detailResponseCleansLegacyDirtyFullText() {
        NewsArticleEntity article = articleWithText(
                6L,
                "Readable merger headline",
                "(function (w, d, s, l, i) { w[l] = w[l] || []; })(window, document, 'script', 'dataLayer', 'GTM'); *,::after,::before{box-sizing:border-box}"
        );
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(articleRepository.findById(6L)).thenReturn(Optional.of(article));
        when(eventRepository.findByArticle(article)).thenReturn(Optional.empty());
        ArticleController controller = controller(articleRepository, eventRepository);

        ArticleController.ArticleDetailResponse response = controller.article(6L);

        assertThat(response.fullText()).isEqualTo("Readable merger headline");
        assertThat(response.fullText()).doesNotContain("dataLayer");
        assertThat(response.fullText()).doesNotContain("function (w, d, s, l, i)");
        assertThat(response.fullText()).doesNotContain("box-sizing");
    }

    @Test
    void manualReviewMarksArticleEventUseful() {
        NewsArticleEntity article = article(7L, "Target to be acquired", "https://example.com/review");
        DetectedEventEntity event = event(article, 90, CandidateStrength.HIGH);
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(articleRepository.findById(7L)).thenReturn(Optional.of(article));
        when(eventRepository.findByArticle(article)).thenReturn(Optional.of(event));
        ArticleController controller = controller(articleRepository, eventRepository);

        ArticleController.ArticleDetailResponse response = controller.updateManualReview(
                7L,
                new ArticleController.ManualReviewRequest(ManualReviewStatus.USEFUL, ManualReviewReason.GOOD_SIGNAL, "Looks actionable")
        );

        assertThat(response.manualReviewStatus()).isEqualTo(ManualReviewStatus.USEFUL);
        assertThat(response.manualReviewReason()).isEqualTo(ManualReviewReason.GOOD_SIGNAL);
        assertThat(response.manualReviewNote()).isEqualTo("Looks actionable");
        assertThat(response.manualReviewedAt()).isNotNull();
        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.HIGH_PRIORITY_SIGNAL);
    }

    @Test
    void manualReviewCanStoreIgnoredReason() {
        NewsArticleEntity article = article(10L, "Private company acquisition", "https://example.com/private");
        DetectedEventEntity event = event(article, 90, CandidateStrength.HIGH);
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(articleRepository.findById(10L)).thenReturn(Optional.of(article));
        when(eventRepository.findByArticle(article)).thenReturn(Optional.of(event));
        ArticleController controller = controller(articleRepository, eventRepository);

        ArticleController.ArticleDetailResponse response = controller.updateManualReview(
                10L,
                new ArticleController.ManualReviewRequest(ManualReviewStatus.IGNORED, ManualReviewReason.PRIVATE_COMPANY, "No public target")
        );

        assertThat(response.manualReviewStatus()).isEqualTo(ManualReviewStatus.IGNORED);
        assertThat(response.manualReviewReason()).isEqualTo(ManualReviewReason.PRIVATE_COMPANY);
        assertThat(response.manualReviewNote()).isEqualTo("No public target");
        assertThat(response.manualReviewedAt()).isNotNull();
    }

    @Test
    void manualReviewCanResetArticleEventToPending() {
        NewsArticleEntity article = article(8L, "Target to be acquired", "https://example.com/reset");
        DetectedEventEntity event = event(article, 90, CandidateStrength.HIGH);
        event.updateManualReview(ManualReviewStatus.IGNORED, ManualReviewReason.PRIVATE_COMPANY, "Not useful");
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(articleRepository.findById(8L)).thenReturn(Optional.of(article));
        when(eventRepository.findByArticle(article)).thenReturn(Optional.of(event));
        ArticleController controller = controller(articleRepository, eventRepository);

        ArticleController.ArticleDetailResponse response = controller.updateManualReview(
                8L,
                new ArticleController.ManualReviewRequest(ManualReviewStatus.PENDING, ManualReviewReason.PRIVATE_COMPANY, "")
        );

        assertThat(response.manualReviewStatus()).isEqualTo(ManualReviewStatus.PENDING);
        assertThat(response.manualReviewReason()).isNull();
        assertThat(response.manualReviewNote()).isNull();
        assertThat(response.manualReviewedAt()).isNull();
    }

    @Test
    void manualReviewReturnsBadRequestForArticleWithoutDetectedEvent() {
        NewsArticleEntity article = article(9L, "Regular article", "https://example.com/no-event");
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(articleRepository.findById(9L)).thenReturn(Optional.of(article));
        when(eventRepository.findByArticle(article)).thenReturn(Optional.empty());
        ArticleController controller = controller(articleRepository, eventRepository);

        assertThatThrownBy(() -> controller.updateManualReview(
                9L,
                new ArticleController.ManualReviewRequest(ManualReviewStatus.USEFUL, ManualReviewReason.GOOD_SIGNAL, null)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    private NewsArticleEntity article(Long id, String headline, String url) {
        return article(id, headline, "Shareholders will receive $5.00 per share in cash.", url);
    }

    private ArticleController controller(
            NewsArticleRepository articleRepository,
            DetectedEventRepository eventRepository
    ) {
        return new ArticleController(
                articleRepository,
                eventRepository,
                new CandidateReviewInsightService(),
                new DealTermsExtractionService(),
                new DealRelevanceService(),
                new DealStageDetectionService()
        );
    }

    private NewsArticleEntity articleWithText(Long id, String headline, String articleText) {
        return article(id, headline, articleText, "https://example.com/" + id);
    }

    private NewsArticleEntity article(Long id, String headline, String articleText, String url) {
        NewsSourceEntity source = new NewsSourceEntity("Test Source", NewsSourceType.RSS, "https://example.com/feed");
        setId(source, id + 100);
        NewsArticleEntity article = new NewsArticleEntity(
                source,
                "hash-" + id,
                "TEST",
                "Test Company",
                headline,
                articleText,
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
                candidateStrength == CandidateStrength.HIGH,
                "HIGH candidate from trusted source with positive score.",
                "definitive agreement|per share in cash",
                "",
                "",
                "Matched test candidate"
        );
        setId(event, 50L);
        return event;
    }

    private void setId(Object entity, Long id) {
        setField(entity, "id", id);
    }

    private void setField(Object entity, String name, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
