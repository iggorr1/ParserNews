package com.parsernews.web;

import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.config.SecurityConfig;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.persistence.SecSignalPriority;
import com.parsernews.persistence.SecSignalType;
import com.parsernews.persistence.SecWatchlistCompanyEntity;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.SafetyGuardService;
import com.parsernews.service.SecFilingDocumentFetcher;
import com.parsernews.service.SecWatchlistManagerService;
import com.parsernews.service.SecWatchlistScanner;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class SecControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SecWatchlistScanner secWatchlistScanner;

    @MockitoBean
    private SecFilingRepository filingRepository;

    @MockitoBean
    private SecFilingDocumentFetcher documentFetcher;

    @MockitoBean
    private SecWatchlistManagerService watchlistManagerService;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @Test
    void secEndpointsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/sec/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void watchlistEndpointsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/sec/watchlist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedSecStatusReturnsOk() throws Exception {
        when(secWatchlistScanner.status()).thenReturn(new SecWatchlistScanner.SecStatus(
                false,
                false,
                SecWatchlistManagerService.WatchlistSource.NONE,
                0,
                0,
                0,
                0,
                0,
                20,
                0,
                "SEC scanner disabled or watchlist empty"
        ));

        mockMvc.perform(get("/api/sec/status").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.activeWatchlistSource").value("NONE"))
                .andExpect(jsonPath("$.activeWatchlistSize").value(0))
                .andExpect(jsonPath("$.watchlistSize").value(0))
                .andExpect(jsonPath("$.warning").value("SEC scanner disabled or watchlist empty"))
                .andExpect(jsonPath("$.maxFilingsPerCik").value(20));
    }

    @Test
    void authenticatedSecScanReturnsSummary() throws Exception {
        when(secWatchlistScanner.scan()).thenReturn(new SecWatchlistScanner.SecScanSummary(
                true,
                1,
                4,
                3,
                2,
                1,
                "SEC scan completed."
        ));

        mockMvc.perform(post("/api/sec/scan").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedFilings").value(2))
                .andExpect(jsonPath("$.duplicatesSkipped").value(1));
    }

    @Test
    void authenticatedWatchlistListReturnsEntries() throws Exception {
        when(watchlistManagerService.listEntries()).thenReturn(List.of(
                new SecWatchlistCompanyEntity("320193", "Apple Inc.", "AAPL", "Smoke test", true)
        ));

        mockMvc.perform(get("/api/sec/watchlist").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cik").value("320193"))
                .andExpect(jsonPath("$[0].companyName").value("Apple Inc."))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void authenticatedWatchlistAddReturnsEntry() throws Exception {
        when(watchlistManagerService.addEntry(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SecWatchlistCompanyEntity("789019", "Microsoft Corp.", "MSFT", null, true));

        mockMvc.perform(post("/api/sec/watchlist")
                        .with(httpBasic("tester", "secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cik":"789019","companyName":"Microsoft Corp.","ticker":"MSFT","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cik").value("789019"))
                .andExpect(jsonPath("$.companyName").value("Microsoft Corp."));
    }

    @Test
    void duplicateWatchlistCikReturnsBadRequest() throws Exception {
        when(watchlistManagerService.addEntry(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new SecWatchlistManagerService.DuplicateSecWatchlistCompanyException("SEC watchlist CIK already exists: 320193"));

        mockMvc.perform(post("/api/sec/watchlist")
                        .with(httpBasic("tester", "secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cik":"320193","companyName":"Apple Inc.","enabled":true}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticatedWatchlistUpdateAndDeleteWork() throws Exception {
        when(watchlistManagerService.updateEntry(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SecWatchlistCompanyEntity("320193", "Apple Inc.", "AAPL", null, false));

        mockMvc.perform(patch("/api/sec/watchlist/1")
                        .with(httpBasic("tester", "secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Apple Inc.","ticker":"AAPL","enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(delete("/api/sec/watchlist/1").with(httpBasic("tester", "secret")))
                .andExpect(status().isNoContent());
    }

    @Test
    void authenticatedSecDocumentFetchReturnsSummary() throws Exception {
        when(documentFetcher.fetchPendingDocuments()).thenReturn(new SecFilingDocumentFetcher.SecDocumentFetchSummary(
                2,
                1,
                0,
                1
        ));

        mockMvc.perform(post("/api/sec/fetch-documents").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptedCount").value(2))
                .andExpect(jsonPath("$.fetchedCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(1));
    }

    @Test
    void authenticatedSecManualReviewUpdatesFiling() throws Exception {
        SecFilingEntity filing = filing();
        when(filingRepository.findById(1L)).thenReturn(Optional.of(filing));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/sec/filings/1/manual-review")
                        .with(httpBasic("tester", "secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"USEFUL","reason":"GOOD_SIGNAL","note":"Looks important"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manualReviewStatus").value("USEFUL"))
                .andExpect(jsonPath("$.manualReviewReason").value("GOOD_SIGNAL"))
                .andExpect(jsonPath("$.manualReviewNote").value("Looks important"));
    }

    @Test
    void authenticatedReviewedSecFilingsReturnsReviewedOnly() throws Exception {
        SecFilingEntity filing = filing();
        filing.updateManualReview(ManualReviewStatus.IGNORED, ManualReviewReason.FALSE_POSITIVE, "Noise");
        when(filingRepository.findTop200ByManualReviewStatusInOrderByManualReviewedAtDesc(List.of(ManualReviewStatus.USEFUL, ManualReviewStatus.IGNORED)))
                .thenReturn(List.of(filing));

        mockMvc.perform(get("/api/sec/filings/reviewed").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].manualReviewStatus").value("IGNORED"))
                .andExpect(jsonPath("$[0].manualReviewReason").value("FALSE_POSITIVE"));
    }

    @Test
    void authenticatedSecCsvExportReturnsCsv() throws Exception {
        SecFilingEntity filing = filing();
        filing.markDocumentFetched(
                filing.getFilingUrl(),
                "Merger text",
                "HIGH",
                "Document text mentions merger agreement language.",
                SecSignalType.MERGER_AGREEMENT,
                SecSignalPriority.HIGH,
                "Document text mentions merger agreement language.",
                null
        );
        when(filingRepository.findAll()).thenReturn(List.of(filing));

        mockMvc.perform(get("/api/sec/filings/export.csv")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("secSignalPriority")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("MICROSOFT CORP")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("MERGER_AGREEMENT")));
    }

    private SecFilingEntity filing() {
        return new SecFilingEntity(
                "0000789019",
                "MICROSOFT CORP",
                "8-K",
                LocalDate.of(2026, 6, 5),
                "0001193125-26-258667",
                "d26760d8k.htm",
                "https://www.sec.gov/Archives/edgar/data/789019/000119312526258667/d26760d8k.htm",
                "WATCHLIST_FORM",
                "Interesting SEC form for watchlist review."
        );
    }
}
