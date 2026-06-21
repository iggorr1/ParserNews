package com.parsernews.service;

import com.parsernews.config.SecScannerSettings;
import com.parsernews.persistence.SecWatchlistCompanyEntity;
import com.parsernews.persistence.SecWatchlistCompanyRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecWatchlistManagerServiceTest {
    @Test
    void resolvesEnabledDbEntriesBeforeEnvWatchlist() {
        SecWatchlistCompanyRepository repository = mock(SecWatchlistCompanyRepository.class);
        when(repository.count()).thenReturn(2L);
        when(repository.countByEnabledTrue()).thenReturn(1L);
        when(repository.findByEnabledTrueOrderByCompanyNameAscCikAsc())
                .thenReturn(List.of(new SecWatchlistCompanyEntity("320193", "Apple Inc.", "AAPL", null, true)));
        SecWatchlistManagerService service = new SecWatchlistManagerService(
                new SecScannerSettings(true, "789019", 20),
                repository
        );

        SecWatchlistManagerService.ResolvedWatchlist watchlist = service.resolveActiveWatchlist();

        assertThat(watchlist.source()).isEqualTo(SecWatchlistManagerService.WatchlistSource.DB);
        assertThat(watchlist.ciks()).containsExactly("320193");
        assertThat(watchlist.dbWatchlistSize()).isEqualTo(2);
        assertThat(watchlist.envWatchlistSize()).isEqualTo(1);
    }

    @Test
    void fallsBackToEnvWatchlistWhenDbIsEmpty() {
        SecWatchlistCompanyRepository repository = mock(SecWatchlistCompanyRepository.class);
        when(repository.count()).thenReturn(0L);
        SecWatchlistManagerService service = new SecWatchlistManagerService(
                new SecScannerSettings(true, "0000320193,789019", 20),
                repository
        );

        SecWatchlistManagerService.ResolvedWatchlist watchlist = service.resolveActiveWatchlist();

        assertThat(watchlist.source()).isEqualTo(SecWatchlistManagerService.WatchlistSource.ENV);
        assertThat(watchlist.ciks()).containsExactly("320193", "789019");
        assertThat(watchlist.activeWatchlistSize()).isEqualTo(2);
    }

    @Test
    void duplicateCikIsRejected() {
        SecWatchlistCompanyRepository repository = mock(SecWatchlistCompanyRepository.class);
        when(repository.existsByCik("320193")).thenReturn(true);
        SecWatchlistManagerService service = new SecWatchlistManagerService(
                new SecScannerSettings(false, "", 20),
                repository
        );

        assertThatThrownBy(() -> service.addEntry(new SecWatchlistManagerService.WatchlistRequest(
                "0000320193",
                "Apple Inc.",
                "AAPL",
                null,
                true
        ))).isInstanceOf(SecWatchlistManagerService.DuplicateSecWatchlistCompanyException.class);
    }

    @Test
    void invalidCikIsRejected() {
        assertThatThrownBy(() -> SecWatchlistManagerService.normalizeCik("CIK 320193"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CIK must contain 1 to 10 digits");
    }
}
