package com.parsernews.service;

import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.persistence.SecSignalPriority;
import com.parsernews.persistence.SecSignalType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecFilingDocumentFetcherTest {
    @Test
    void buildsSecArchiveDocumentUrl() {
        assertThat(SecFilingDocumentFetcher.buildDocumentUrl("0000320193", "0000320193-26-000001", "aapl-8k.htm"))
                .isEqualTo("https://www.sec.gov/Archives/edgar/data/320193/000032019326000001/aapl-8k.htm");
    }

    @Test
    void cleansHtmlAndRemovesScriptAndStyleBoilerplate() {
        String cleaned = SecFilingDocumentFetcher.cleanText("""
                <html><head>
                <script>dataLayer.push("x");</script>
                <style>body { box-sizing: border-box; }</style>
                </head><body><p>Agreement&#160;and Plan of Merger was signed.</p></body></html>
                """);

        assertThat(cleaned).contains("Agreement and Plan of Merger was signed.");
        assertThat(cleaned).doesNotContain("dataLayer", "box-sizing", "<script", "&#160;");
    }

    @Test
    void detectsStrongDocumentSignals() {
        assertThat(SecFilingDocumentFetcher.analyze("The parties launched a tender offer.").priority())
                .isEqualTo(SecSignalPriority.HIGH);
        assertThat(SecFilingDocumentFetcher.analyze("The parties launched a tender offer.").type())
                .isEqualTo(SecSignalType.TENDER_OFFER);
        assertThat(SecFilingDocumentFetcher.analyze("The parties entered into an Agreement and Plan of Merger.").priority())
                .isEqualTo(SecSignalPriority.HIGH);
        assertThat(SecFilingDocumentFetcher.analyze("This filing includes a definitive proxy statement.").priority())
                .isEqualTo(SecSignalPriority.MEDIUM);
        assertThat(SecFilingDocumentFetcher.analyze("Ordinary quarterly disclosure.").priority())
                .isEqualTo(SecSignalPriority.NONE);
    }

    @Test
    void fetchesSavedFilingDocumentAndStoresCleanSnippet() {
        SecFilingEntity filing = filing();
        SecFilingRepository repository = mock(SecFilingRepository.class);
        when(repository.findTop50ByDocumentFetchedAtIsNullOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of(filing));
        SecFilingDocumentFetcher fetcher = new SecFilingDocumentFetcher(repository, url -> """
                <html><script>googletagmanager</script><body>
                <p>The company entered into an agreement and plan of merger with Buyer Corp.</p>
                </body></html>
                """);

        SecFilingDocumentFetcher.SecDocumentFetchSummary summary = fetcher.fetchPendingDocuments();

        assertThat(summary.attemptedCount()).isEqualTo(1);
        assertThat(summary.fetchedCount()).isEqualTo(1);
        assertThat(filing.getDocumentFetchStatus()).isEqualTo("FETCHED");
        assertThat(filing.getDocumentTextSnippet()).contains("agreement and plan of merger");
        assertThat(filing.getDocumentTextSnippet()).doesNotContain("googletagmanager");
        assertThat(filing.getDocumentSignalStrength()).isEqualTo("HIGH");
        assertThat(filing.getSecSignalType()).isEqualTo(SecSignalType.MERGER_AGREEMENT);
        assertThat(filing.getSecSignalPriority()).isEqualTo(SecSignalPriority.HIGH);
    }

    @Test
    void skipsAlreadyFetchedDocument() {
        SecFilingEntity filing = filing();
        filing.markDocumentFetched("https://example.test/doc.htm", "Already fetched", "NONE", "Already analyzed.");
        SecFilingRepository repository = mock(SecFilingRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(filing));
        AtomicInteger calls = new AtomicInteger();
        SecFilingDocumentFetcher fetcher = new SecFilingDocumentFetcher(repository, url -> {
            calls.incrementAndGet();
            return "Should not fetch";
        });

        SecFilingEntity result = fetcher.fetchDocument(1L);

        assertThat(result.getDocumentTextSnippet()).isEqualTo("Already fetched");
        assertThat(calls).hasValue(0);
    }

    private SecFilingEntity filing() {
        return new SecFilingEntity(
                "0000320193",
                "Apple Inc.",
                "8-K",
                LocalDate.of(2026, 6, 18),
                "0000320193-26-000001",
                "aapl-8k.htm",
                "https://www.sec.gov/Archives/edgar/data/320193/000032019326000001/aapl-8k.htm",
                "MERGER_AGREEMENT",
                "Matched merger agreement language."
        );
    }
}
