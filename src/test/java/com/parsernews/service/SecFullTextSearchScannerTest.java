package com.parsernews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.config.SecFullTextSearchSettings;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.persistence.SecSignalType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecFullTextSearchScannerTest {

    private SecFullTextSearchScanner scanner() {
        SecFullTextSearchSettings settings = new SecFullTextSearchSettings(true, null, null, 3, 30, 0, null);
        return new SecFullTextSearchScanner(settings, mock(SecFullTextSearchClient.class),
                mock(SecFilingRepository.class), mock(SecDocumentClient.class), new ObjectMapper());
    }

    @Test
    void parsesCompanyTickerCikAndAccessionFromEftsHit() {
        String json = """
                {"hits":{"total":{"value":2},"hits":[
                  {"_id":"0001299709-26-000047:ax-20260706.htm","_source":{
                     "adsh":"0001299709-26-000047","file_type":"8-K","file_date":"2026-07-07",
                     "ciks":["0001299709"],"display_names":["Axos Financial, Inc.  (AX)  (CIK 0001299709)"]}},
                  {"_id":"0001299709-26-000047:pressrelease.htm","_source":{
                     "adsh":"0001299709-26-000047","file_type":"EX-99.1","file_date":"2026-07-07",
                     "ciks":["0001299709"],"display_names":["Axos Financial, Inc.  (AX)  (CIK 0001299709)"]}}
                ]}}
                """;

        List<SecFullTextSearchScanner.DiscoveredFtsFiling> hits =
                scanner().parseHits(json, "definitive agreement to acquire");

        assertThat(hits).hasSize(2); // dedup by accession happens in scan(), not parseHits
        SecFullTextSearchScanner.DiscoveredFtsFiling first = hits.get(0);
        assertThat(first.accessionNumber()).isEqualTo("0001299709-26-000047");
        assertThat(first.companyName()).isEqualTo("Axos Financial, Inc.");
        assertThat(first.ticker()).isEqualTo("AX");
        assertThat(first.cik()).isEqualTo("0001299709");
        assertThat(first.form()).isEqualTo("8-K");
        assertThat(first.signalType()).isEqualTo(SecSignalType.MERGER_AGREEMENT);
        assertThat(first.filingUrl()).contains("/1299709/000129970926000047/");
    }

    @Test
    void tenderOfferQueryMapsToTenderOfferSignal() {
        String json = "{\"hits\":{\"hits\":[{\"_id\":\"0000000000-26-000001:d.htm\",\"_source\":{"
                + "\"adsh\":\"0000000000-26-000001\",\"file_type\":\"SC TO-T\",\"file_date\":\"2026-07-07\","
                + "\"ciks\":[\"0000000000\"],\"display_names\":[\"Target Co (CIK 0000000000)\"]}}]}}";

        List<SecFullTextSearchScanner.DiscoveredFtsFiling> hits =
                scanner().parseHits(json, "commence a tender offer");

        assertThat(hits).singleElement().satisfies(h -> {
            assertThat(h.signalType()).isEqualTo(SecSignalType.TENDER_OFFER);
            assertThat(h.ticker()).isNull();               // no ticker in display name
            assertThat(h.companyName()).isEqualTo("Target Co");
        });
    }

    @Test
    void disabledScanReturnsWithoutSearching() {
        SecFullTextSearchSettings disabled = new SecFullTextSearchSettings(false, null, null, 3, 30, 0, null);
        SecFullTextSearchScanner s = new SecFullTextSearchScanner(disabled, mock(SecFullTextSearchClient.class),
                mock(SecFilingRepository.class), mock(SecDocumentClient.class), new ObjectMapper());

        SecFullTextSearchScanner.FtsSummary summary = s.scan();

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.changed()).isZero();
    }
}
