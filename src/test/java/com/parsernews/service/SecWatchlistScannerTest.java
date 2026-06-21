package com.parsernews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.config.SecScannerSettings;
import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecWatchlistScannerTest {
    @Test
    void padsCikToTenDigits() {
        assertThat(SecWatchlistScanner.padCik("320193")).isEqualTo("0000320193");
        assertThat(SecWatchlistScanner.padCik("0001067983")).isEqualTo("0001067983");
        assertThat(SecWatchlistScanner.padCik("CIK 789019")).isEqualTo("0000789019");
        assertThatThrownBy(() -> SecWatchlistScanner.padCik(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesSecJsonAndFiltersInterestingForms() throws Exception {
        SecFilingRepository repository = mock(SecFilingRepository.class);
        SecWatchlistScanner scanner = scanner(true, "320193", repository, json());

        SecWatchlistScanner.SecScanSummary summary = scanner.scan();

        ArgumentCaptor<SecFilingEntity> captor = ArgumentCaptor.forClass(SecFilingEntity.class);
        verify(repository, org.mockito.Mockito.times(3)).save(captor.capture());
        List<SecFilingEntity> saved = captor.getAllValues();
        assertThat(summary.fetchedFilings()).isEqualTo(4);
        assertThat(summary.matchedFilings()).isEqualTo(3);
        assertThat(summary.savedFilings()).isEqualTo(3);
        assertThat(saved).extracting(SecFilingEntity::getForm)
                .containsExactly("8-K", "SC TO-I", "DEFM14A");
        assertThat(saved.getFirst().getCik()).isEqualTo("0000320193");
        assertThat(saved.getFirst().getCompanyName()).isEqualTo("Apple Inc.");
        assertThat(saved.getFirst().getFilingUrl())
                .isEqualTo("https://www.sec.gov/Archives/edgar/data/320193/000032019326000001/aapl-8k.htm");
    }

    @Test
    void dedupesByAccessionNumber() throws Exception {
        SecFilingRepository repository = mock(SecFilingRepository.class);
        when(repository.existsByAccessionNumber("0000320193-26-000001")).thenReturn(true);
        SecWatchlistScanner scanner = scanner(true, "320193", repository, json());

        SecWatchlistScanner.SecScanSummary summary = scanner.scan();

        assertThat(summary.duplicatesSkipped()).isEqualTo(1);
        assertThat(summary.savedFilings()).isEqualTo(2);
        verify(repository, org.mockito.Mockito.times(2)).save(any(SecFilingEntity.class));
    }

    @Test
    void disabledScannerDoesNotFetchOrSave() {
        SecFilingRepository repository = mock(SecFilingRepository.class);
        SecSubmissionsClient client = paddedCik -> {
            throw new AssertionError("Should not fetch when disabled");
        };
        SecWatchlistScanner scanner = new SecWatchlistScanner(
                new SecScannerSettings(false, "320193", 20),
                client,
                repository,
                new ObjectMapper()
        );

        SecWatchlistScanner.SecScanSummary summary = scanner.scan();

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.configured()).isFalse();
        assertThat(summary.watchlistSize()).isEqualTo(1);
        verify(repository, never()).save(any(SecFilingEntity.class));
    }

    @Test
    void statusShowsDisabledOrEmptyWarning() {
        SecWatchlistScanner scanner = scanner(false, "", mock(SecFilingRepository.class), json());

        SecWatchlistScanner.SecStatus status = scanner.status();

        assertThat(status.enabled()).isFalse();
        assertThat(status.configured()).isFalse();
        assertThat(status.watchlistSize()).isZero();
        assertThat(status.warning()).isEqualTo("SEC scanner disabled or watchlist empty");
    }

    @Test
    void statusShowsConfiguredWhenEnabledAndWatchlistPresent() {
        SecWatchlistScanner scanner = scanner(true, "320193,789019", mock(SecFilingRepository.class), json());

        SecWatchlistScanner.SecStatus status = scanner.status();

        assertThat(status.enabled()).isTrue();
        assertThat(status.configured()).isTrue();
        assertThat(status.watchlistSize()).isEqualTo(2);
        assertThat(status.maxFilingsPerCik()).isEqualTo(20);
        assertThat(status.warning()).isNull();
    }

    @Test
    void enabledScannerWithEmptyWatchlistReturnsWarningWithoutFetch() {
        SecFilingRepository repository = mock(SecFilingRepository.class);
        SecSubmissionsClient client = paddedCik -> {
            throw new AssertionError("Should not fetch when watchlist is empty");
        };
        SecWatchlistScanner scanner = new SecWatchlistScanner(
                new SecScannerSettings(true, "", 20),
                client,
                repository,
                new ObjectMapper()
        );

        SecWatchlistScanner.SecScanSummary summary = scanner.scan();

        assertThat(summary.enabled()).isTrue();
        assertThat(summary.configured()).isFalse();
        assertThat(summary.watchlistSize()).isZero();
        assertThat(summary.message()).isEqualTo("SEC scanner watchlist is empty.");
        verify(repository, never()).save(any(SecFilingEntity.class));
    }

    @Test
    void detectsSignalsFromFormAndDocumentName() {
        SecWatchlistScanner scanner = scanner(true, "320193", mock(SecFilingRepository.class), json());

        assertThat(scanner.detectSignal("SC TO-T", "offer-to-purchase.htm").signalType())
                .isEqualTo("TENDER_OFFER");
        assertThat(scanner.detectSignal("DEFM14A", "definitive-proxy.htm").signalType())
                .isEqualTo("DEFINITIVE_PROXY");
        assertThat(scanner.detectSignal("8-K", "merger-agreement.htm").signalType())
                .isEqualTo("MERGER_AGREEMENT");
        assertThat(scanner.detectSignal("S-4", "business-combination.htm").signalType())
                .isEqualTo("BUSINESS_COMBINATION");
    }

    @Test
    void formFilteringMatchesOnlyMvpForms() {
        SecWatchlistScanner scanner = scanner(true, "320193", mock(SecFilingRepository.class), json());

        assertThat(scanner.isInterestingForm("8-K")).isTrue();
        assertThat(scanner.isInterestingForm("SC TO-I")).isTrue();
        assertThat(scanner.isInterestingForm("10-Q")).isFalse();
    }

    private SecWatchlistScanner scanner(
            boolean enabled,
            String watchlist,
            SecFilingRepository repository,
            String json
    ) {
        return new SecWatchlistScanner(
                new SecScannerSettings(enabled, watchlist, 20),
                new FakeSecSubmissionsClient(json),
                repository,
                new ObjectMapper()
        );
    }

    private String json() {
        return """
                {
                  "cik": "320193",
                  "name": "Apple Inc.",
                  "filings": {
                    "recent": {
                      "form": ["8-K", "10-Q", "SC TO-I", "DEFM14A"],
                      "filingDate": ["2026-06-18", "2026-06-17", "2026-06-16", "2026-06-15"],
                      "accessionNumber": [
                        "0000320193-26-000001",
                        "0000320193-26-000002",
                        "0000320193-26-000003",
                        "0000320193-26-000004"
                      ],
                      "primaryDocument": [
                        "aapl-8k.htm",
                        "aapl-10q.htm",
                        "offer-to-purchase.htm",
                        "definitive-proxy.htm"
                      ]
                    }
                  }
                }
                """;
    }

    private record FakeSecSubmissionsClient(String json) implements SecSubmissionsClient {
        @Override
        public String fetchSubmissionsJson(String paddedCik) throws IOException {
            assertThat(paddedCik).hasSize(10);
            return json;
        }
    }
}
