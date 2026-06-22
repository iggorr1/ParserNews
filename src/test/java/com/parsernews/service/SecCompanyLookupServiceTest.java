package com.parsernews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.persistence.SecWatchlistCompanyEntity;
import com.parsernews.persistence.SecWatchlistCompanyRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecCompanyLookupServiceTest {
    @Test
    void exactTickerMatchIsReturnedFirst() {
        SecCompanyLookupService service = serviceWithJson("""
                {
                  "0": {"cik_str": 320193, "ticker": "AAPL", "title": "Apple Inc."},
                  "1": {"cik_str": 111111, "ticker": "AAPLX", "title": "Apple Example Corp."}
                }
                """, List.of());

        List<SecCompanyLookupService.SecCompanyLookupResult> results = service.search("AAPL");

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().ticker()).isEqualTo("AAPL");
        assertThat(results.getFirst().matchType()).isEqualTo(SecCompanyLookupService.MatchType.EXACT_TICKER);
        assertThat(results.getFirst().cik()).isEqualTo("320193");
    }

    @Test
    void partialCompanyNameSearchWorks() {
        SecCompanyLookupService service = serviceWithJson("""
                {
                  "0": {"cik_str": 789019, "ticker": "MSFT", "title": "MICROSOFT CORP"},
                  "1": {"cik_str": 1318605, "ticker": "TSLA", "title": "Tesla, Inc."}
                }
                """, List.of());

        List<SecCompanyLookupService.SecCompanyLookupResult> results = service.search("soft");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().ticker()).isEqualTo("MSFT");
        assertThat(results.getFirst().matchType()).isEqualTo(SecCompanyLookupService.MatchType.PARTIAL_NAME);
    }

    @Test
    void alreadyInWatchlistFlagIsIncluded() {
        SecCompanyLookupService service = serviceWithJson("""
                {
                  "0": {"cik_str": 320193, "ticker": "AAPL", "title": "Apple Inc."}
                }
                """, List.of(new SecWatchlistCompanyEntity("320193", "Apple Inc.", "AAPL", null, true)));

        List<SecCompanyLookupService.SecCompanyLookupResult> results = service.search("AAPL");

        assertThat(results.getFirst().alreadyInWatchlist()).isTrue();
        assertThat(results.getFirst().enabledInWatchlist()).isTrue();
    }

    private SecCompanyLookupService serviceWithJson(String json, List<SecWatchlistCompanyEntity> entries) {
        SecCompanyTickerClient client = () -> json;
        SecWatchlistCompanyRepository repository = mock(SecWatchlistCompanyRepository.class);
        when(repository.findAll()).thenReturn(entries);
        return new SecCompanyLookupService(client, repository, new ObjectMapper());
    }
}
