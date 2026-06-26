package com.parsernews.service;

import com.parsernews.config.SecDiscoverySettings;
import com.parsernews.persistence.SecDiscoveryRunEntity;
import com.parsernews.persistence.SecDiscoveryRunRepository;
import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecDiscoveryScannerTest {
    @Test
    void disabledDiscoveryIsSafeNoOp() throws Exception {
        SecCurrentFilingsClient client = mock(SecCurrentFilingsClient.class);
        SecFilingRepository filingRepository = mock(SecFilingRepository.class);
        SecDiscoveryRunRepository runRepository = mock(SecDiscoveryRunRepository.class);

        SecDiscoveryScanner scanner = scanner(settings(false, "8-K", false), client, filingRepository, runRepository);

        SecDiscoveryScanner.SecDiscoverySummary summary = scanner.scan();

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.newFilings()).isZero();
        assertThat(summary.errors()).contains("SEC discovery is disabled.");
        verify(client, never()).fetchCurrentFilingsAtom(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(filingRepository, never()).save(any());
        verify(runRepository, never()).save(any());
    }

    @Test
    void discoversConfiguredFormsAndDedupesByAccessionNumber() throws Exception {
        SecCurrentFilingsClient client = (form, count) -> atom(
                entry("8-K - Apogee Therapeutics, Inc. (1974640)", "2026-06-24T12:00:00-04:00",
                        "https://www.sec.gov/Archives/edgar/data/1974640/000114036126025841/apge-8k.htm"),
                entry("8-K - Apogee Therapeutics, Inc. (1974640)", "2026-06-24T12:00:00-04:00",
                        "https://www.sec.gov/Archives/edgar/data/1974640/000114036126025841/apge-8k.htm")
        );
        SecFilingRepository filingRepository = mock(SecFilingRepository.class);
        SecDiscoveryRunRepository runRepository = runRepository();
        when(filingRepository.findByAccessionNumber("0001140361-26-025841")).thenReturn(Optional.empty());
        when(filingRepository.save(any(SecFilingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SecDiscoveryScanner scanner = scanner(settings(true, "8-K", false), client, filingRepository, runRepository);

        SecDiscoveryScanner.SecDiscoverySummary summary = scanner.scan();

        assertThat(summary.scannedFilings()).isEqualTo(2);
        assertThat(summary.newFilings()).isEqualTo(1);
        assertThat(summary.duplicateFilings()).isEqualTo(1);
        assertThat(summary.createdOrUpdatedDealGroups()).isEqualTo(1);
        verify(filingRepository).save(org.mockito.ArgumentMatchers.argThat(filing ->
                filing.getCik().equals("0001974640")
                        && filing.getCompanyName().equals("Apogee Therapeutics, Inc.")
                        && filing.getForm().equals("8-K")
                        && filing.getAccessionNumber().equals("0001140361-26-025841")
                        && filing.getPrimaryDocument().equals("apge-8k.htm")
        ));
    }

    @Test
    void skipsUnconfiguredFormsFromParsedFeed() {
        SecDiscoveryScanner scanner = scanner(
                settings(true, "8-K", false),
                mock(SecCurrentFilingsClient.class),
                mock(SecFilingRepository.class),
                runRepository()
        );

        List<SecDiscoveryScanner.DiscoveredSecFiling> filings = scanner.parseCurrentFeed("10-Q", atom(
                entry("10-Q - Example Corp (123456)", "2026-06-24T12:00:00-04:00",
                        "https://www.sec.gov/Archives/edgar/data/123456/000012345626000001/example-10q.htm")
        ));

        assertThat(filings).singleElement()
                .satisfies(filing -> assertThat(filing.form()).isEqualTo("10-Q"));
    }

    @Test
    void discoveryDoesNotDependOnOpenAiOrTelegramServices() {
        assertThat(SecDiscoveryScanner.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().contains("OpenAi"))
                .noneMatch(field -> field.getType().getName().contains("Telegram"));
    }

    @Test
    void companyFromTitleStripsFormPrefixAndCikSuffix() {
        SecDiscoveryScanner scanner = scanner(settings(true, "8-K", false), mock(SecCurrentFilingsClient.class),
                mock(SecFilingRepository.class), runRepository());

        List<SecDiscoveryScanner.DiscoveredSecFiling> filings = scanner.parseCurrentFeed("8-K", atom(
                entry("8-K - Apogee Therapeutics, Inc. (1974640)", "2026-06-24T12:00:00-04:00",
                        "https://www.sec.gov/Archives/edgar/data/1974640/000114036126025841/apge-8k.htm")
        ));

        assertThat(filings).singleElement()
                .satisfies(f -> assertThat(f.companyName()).isEqualTo("Apogee Therapeutics, Inc."));
    }

    @Test
    void companyFromTitlePreservesHyphenInCompanyName() {
        SecDiscoveryScanner scanner = scanner(settings(true, "SC TO-T", false), mock(SecCurrentFilingsClient.class),
                mock(SecFilingRepository.class), runRepository());

        List<SecDiscoveryScanner.DiscoveredSecFiling> filings = scanner.parseCurrentFeed("SC TO-T", atom(
                entry("SC TO-T - Some-Target Corp (1234567)", "2026-06-24T12:00:00-04:00",
                        "https://www.sec.gov/Archives/edgar/data/1234567/000012345626000001/offer.htm")
        ));

        assertThat(filings).singleElement()
                .satisfies(f -> assertThat(f.companyName()).isEqualTo("Some-Target Corp"));
    }

    @Test
    void companyFromTitleHandlesAmendedFormPrefix() {
        SecDiscoveryScanner scanner = scanner(settings(true, "SC TO-T/A", false), mock(SecCurrentFilingsClient.class),
                mock(SecFilingRepository.class), runRepository());

        List<SecDiscoveryScanner.DiscoveredSecFiling> filings = scanner.parseCurrentFeed("SC TO-T/A", atom(
                entry("SC TO-T/A - Target Corp (9876543)", "2026-06-24T12:00:00-04:00",
                        "https://www.sec.gov/Archives/edgar/data/9876543/000098765426000001/offer-a.htm")
        ));

        assertThat(filings).singleElement()
                .satisfies(f -> assertThat(f.companyName()).isEqualTo("Target Corp"));
    }

    @Test
    void companyFromTitleReturnsUnknownForBlankTitle() {
        SecDiscoveryScanner scanner = scanner(settings(true, "8-K", false), mock(SecCurrentFilingsClient.class),
                mock(SecFilingRepository.class), runRepository());

        List<SecDiscoveryScanner.DiscoveredSecFiling> filings = scanner.parseCurrentFeed("8-K", atom(
                entry("", "2026-06-24T12:00:00-04:00",
                        "https://www.sec.gov/Archives/edgar/data/1974640/000114036126025841/apge-8k.htm")
        ));

        assertThat(filings).singleElement()
                .satisfies(f -> assertThat(f.companyName()).isEqualTo("UNKNOWN"));
    }

    @Test
    void amendedFormIsAcceptedAndFlaggedWhenBaseFormIsConfigured() throws Exception {
        // EDGAR returns SC TO-T/A entries when queried with type=SC+TO-T/A (fallbackForm = "SC TO-T/A").
        // If only "SC TO-T" is in the configured form list, isFormAllowed() should accept the amendment
        // by matching its base form, and the entity should be flagged as an amendment.
        SecCurrentFilingsClient client = (form, count) -> atom(
                entry("SC TO-T/A - AcmeCorp Inc (1111111)", "2026-06-24T12:00:00-04:00",
                        "https://www.sec.gov/Archives/edgar/data/1111111/000011111126000001/offer-a.htm")
        );
        SecFilingRepository filingRepository = mock(SecFilingRepository.class);
        SecDiscoveryRunRepository runRepository = runRepository();
        when(filingRepository.findByAccessionNumber(any())).thenReturn(Optional.empty());
        when(filingRepository.save(any(SecFilingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Client is queried for "SC TO-T/A" as the fallback form (the configured query form type).
        // Base form "SC TO-T" is in the list so isFormAllowed passes.
        SecDiscoveryScanner scanner = scanner(settings(true, "SC TO-T/A", false), client, filingRepository, runRepository);

        SecDiscoveryScanner.SecDiscoverySummary summary = scanner.scan();

        assertThat(summary.newFilings()).isEqualTo(1);
        verify(filingRepository).save(org.mockito.ArgumentMatchers.argThat(SecFilingEntity::isAmendment));
    }

    @Test
    void amendedFormWithOnlyBaseFormConfiguredIsAlsoAccepted() throws Exception {
        // When the user configures "SC TO-T" but the EDGAR feed returns an "SC TO-T/A" entry,
        // isFormAllowed() should accept it via base form matching.
        SecCurrentFilingsClient client = (form, count) -> atom(
                entry("SC TO-T - TargetCo Inc (2222222)", "2026-06-24T12:00:00-04:00",
                        "https://www.sec.gov/Archives/edgar/data/2222222/000022222226000001/offer.htm"),
                entry("SC TO-T/A - TargetCo Inc (2222222)", "2026-06-24T12:00:00-04:00",
                        "https://www.sec.gov/Archives/edgar/data/2222222/000022222226000002/offer-a.htm")
        );
        SecFilingRepository filingRepository = mock(SecFilingRepository.class);
        SecDiscoveryRunRepository runRepository = runRepository();
        when(filingRepository.findByAccessionNumber(any())).thenReturn(Optional.empty());
        when(filingRepository.save(any(SecFilingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SecDiscoveryScanner scanner = scanner(settings(true, "SC TO-T", false), client, filingRepository, runRepository);

        SecDiscoveryScanner.SecDiscoverySummary summary = scanner.scan();

        assertThat(summary.newFilings()).isEqualTo(2);
        assertThat(summary.skippedFilings()).isZero();
    }

    @Test
    void statusReportsLatestRunAndConfig() {
        SecDiscoveryRunEntity run = new SecDiscoveryRunEntity(java.time.Instant.parse("2026-06-24T12:00:00Z"));
        run.finish("SUCCESS", 4, 2, 1, 2, 0, 0, null);
        SecDiscoveryRunRepository runRepository = mock(SecDiscoveryRunRepository.class);
        when(runRepository.findTopByOrderByStartedAtDesc()).thenReturn(Optional.of(run));

        SecDiscoveryScanner scanner = scanner(
                settings(true, "8-K,SC TO-T", true),
                mock(SecCurrentFilingsClient.class),
                mock(SecFilingRepository.class),
                runRepository
        );

        SecDiscoveryScanner.SecDiscoveryStatus status = scanner.status();

        assertThat(status.enabled()).isTrue();
        assertThat(status.schedulerEnabled()).isFalse();
        assertThat(status.forms()).containsExactly("8-K", "SC TO-T");
        assertThat(status.fetchPrimaryDocument()).isTrue();
        assertThat(status.lastRunSummary().newFilings()).isEqualTo(2);
    }

    private SecDiscoveryScanner scanner(
            SecDiscoverySettings settings,
            SecCurrentFilingsClient client,
            SecFilingRepository filingRepository,
            SecDiscoveryRunRepository runRepository
    ) {
        return new SecDiscoveryScanner(
                settings,
                client,
                filingRepository,
                runRepository,
                mock(SecFilingDocumentFetcher.class)
        );
    }

    private SecDiscoverySettings settings(boolean enabled, String forms, boolean fetchDocuments) {
        return new SecDiscoverySettings(
                enabled,
                20,
                forms,
                fetchDocuments,
                0,
                new SecDiscoverySettings.Scheduler(false, 120000, 300000)
        );
    }

    private SecDiscoveryRunRepository runRepository() {
        SecDiscoveryRunRepository repository = mock(SecDiscoveryRunRepository.class);
        when(repository.save(any(SecDiscoveryRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findTopByOrderByStartedAtDesc()).thenReturn(Optional.empty());
        return repository;
    }

    private String atom(String... entries) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed>
                %s
                </feed>
                """.formatted(String.join("\n", entries));
    }

    private String entry(String title, String updated, String href) {
        return """
                <entry>
                    <title>%s</title>
                    <updated>%s</updated>
                    <link href="%s" />
                </entry>
                """.formatted(title, updated, href);
    }
}
