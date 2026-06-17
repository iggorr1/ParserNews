package com.parsernews.service;

import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AlertMessageFormatterTest {
    @Test
    void formatsMessageWithCandidateDetails() {
        DetectedEventEntity event = event();
        AlertMessageFormatter formatter = new AlertMessageFormatter();

        String message = formatter.format(event);

        assertThat(message).contains("Test Company enters merger agreement");
        assertThat(message).contains("Test Source");
        assertThat(message).contains("example.com");
        assertThat(message).contains("Strength: HIGH");
        assertThat(message).contains("Score: 90");
        assertThat(message).contains("Matched HIGH candidate signal.");
        assertThat(message).contains("https://example.com/news/test-company");
        assertThat(message).contains("Shareholders will receive $5.00 per share in cash.");
    }

    @Test
    void previewSnippetUsesReadableTextInsteadOfScriptBoilerplate() {
        DetectedEventEntity event = event("""
                <script>
                  (function (w, d, s, l, i) { w[l] = w[l] || []; })(window, document, 'script', 'dataLayer', 'GTM');
                </script>
                <style>* { box-sizing: border-box; }</style>
                <p>Readable article sentence starts here with merger agreement details.</p>
                """);
        AlertMessageFormatter formatter = new AlertMessageFormatter();

        String message = formatter.format(event);

        assertThat(message).contains("Snippet: Readable article sentence starts here");
        assertThat(message).doesNotContain("googletagmanager");
        assertThat(message).doesNotContain("dataLayer");
        assertThat(message).doesNotContain("function (w, d, s, l, i)");
        assertThat(message).doesNotContain("box-sizing");
    }

    @Test
    void previewSnippetFallsBackToHeadlineForLegacyPlainBoilerplateText() {
        DetectedEventEntity event = event("""
                (function (w, d, s, l, i) { w[l] = w[l] || []; })(window, document, 'script', 'dataLayer', 'GTM');
                *,::after,::before{box-sizing:border-box}body{margin:0;font-family:Arial}
                """);
        AlertMessageFormatter formatter = new AlertMessageFormatter();

        String message = formatter.format(event);

        assertThat(message).contains("Snippet: Test Company enters merger agreement");
        assertThat(message).doesNotContain("dataLayer");
        assertThat(message).doesNotContain("function (w, d, s, l, i)");
        assertThat(message).doesNotContain("box-sizing");
    }

    private DetectedEventEntity event() {
        return event("Shareholders will receive $5.00 per share in cash.");
    }

    private DetectedEventEntity event(String articleText) {
        NewsSourceEntity source = new NewsSourceEntity("Test Source", NewsSourceType.RSS, "https://example.com/feed");
        NewsArticleEntity article = new NewsArticleEntity(
                source,
                "hash-test",
                "TEST",
                "Test Company",
                "Test Company enters merger agreement",
                articleText,
                "https://example.com/news/test-company",
                Instant.parse("2026-06-17T08:00:00Z")
        );
        return new DetectedEventEntity(
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
                90,
                CandidateStrength.HIGH,
                "Matched HIGH candidate signal.",
                true,
                "HIGH candidate from trusted source with positive score.",
                "merger agreement|per share in cash",
                "",
                "",
                "Matched test candidate"
        );
    }
}
