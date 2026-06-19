package com.parsernews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.config.SecScannerSettings;
import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SecWatchlistScanner {
    private static final Set<String> INTERESTING_FORMS = Set.of(
            "8-K",
            "SC TO-I",
            "SC TO-T",
            "SC 14D9",
            "DEFM14A",
            "PREM14A",
            "425",
            "S-4"
    );

    private final SecScannerSettings settings;
    private final SecSubmissionsClient submissionsClient;
    private final SecFilingRepository filingRepository;
    private final ObjectMapper objectMapper;

    public SecWatchlistScanner(
            SecScannerSettings settings,
            SecSubmissionsClient submissionsClient,
            SecFilingRepository filingRepository,
            ObjectMapper objectMapper
    ) {
        this.settings = settings;
        this.submissionsClient = submissionsClient;
        this.filingRepository = filingRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SecScanSummary scan() {
        if (!settings.enabled()) {
            return new SecScanSummary(false, settings.watchlistCiks().size(), 0, 0, 0, 0, "SEC scanner is disabled.");
        }
        List<String> ciks = settings.watchlistCiks();
        int fetchedFilings = 0;
        int matchedFilings = 0;
        int savedFilings = 0;
        int duplicatesSkipped = 0;
        List<String> errors = new ArrayList<>();

        for (String cik : ciks) {
            String paddedCik = padCik(cik);
            try {
                String json = submissionsClient.fetchSubmissionsJson(paddedCik);
                SecSubmissions submissions = parseSubmissions(paddedCik, json);
                int limit = Math.min(settings.maxFilingsPerCik(), submissions.filings().size());
                for (int index = 0; index < limit; index++) {
                    SecSubmissionFiling filing = submissions.filings().get(index);
                    fetchedFilings++;
                    if (!isInterestingForm(filing.form())) {
                        continue;
                    }
                    matchedFilings++;
                    if (filingRepository.existsByAccessionNumber(filing.accessionNumber())) {
                        duplicatesSkipped++;
                        continue;
                    }
                    filingRepository.save(toEntity(submissions.companyName(), filing));
                    savedFilings++;
                }
                Thread.sleep(150);
            } catch (IOException | RuntimeException exception) {
                errors.add(paddedCik + ": " + exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                errors.add(paddedCik + ": interrupted");
                break;
            }
        }

        String message = errors.isEmpty() ? "SEC scan completed." : String.join("; ", errors);
        return new SecScanSummary(true, ciks.size(), fetchedFilings, matchedFilings, savedFilings, duplicatesSkipped, message);
    }

    public SecStatus status() {
        return new SecStatus(
                settings.enabled(),
                settings.watchlistCiks().size(),
                settings.maxFilingsPerCik(),
                filingRepository.count()
        );
    }

    SecSubmissions parseSubmissions(String fallbackCik, String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        String cik = padCik(root.path("cik").asText(fallbackCik));
        String companyName = root.path("name").asText("UNKNOWN");
        JsonNode recent = root.path("filings").path("recent");
        JsonNode forms = recent.path("form");
        JsonNode filingDates = recent.path("filingDate");
        JsonNode accessions = recent.path("accessionNumber");
        JsonNode primaryDocuments = recent.path("primaryDocument");
        List<SecSubmissionFiling> filings = new ArrayList<>();
        int size = Math.min(forms.size(), Math.min(accessions.size(), primaryDocuments.size()));
        for (int index = 0; index < size; index++) {
            String accessionNumber = accessions.path(index).asText("");
            if (accessionNumber.isBlank()) {
                continue;
            }
            String form = forms.path(index).asText("");
            String primaryDocument = primaryDocuments.path(index).asText("");
            LocalDate filingDate = parseDate(filingDates.path(index).asText(null));
            filings.add(new SecSubmissionFiling(
                    cik,
                    form,
                    filingDate,
                    accessionNumber,
                    primaryDocument,
                    filingUrl(cik, accessionNumber, primaryDocument),
                    detectSignal(form, primaryDocument)
            ));
        }
        return new SecSubmissions(cik, companyName, filings);
    }

    SecSignal detectSignal(String form, String primaryDocument) {
        String normalizedForm = normalizeForm(form);
        String text = ((form == null ? "" : form) + " " + (primaryDocument == null ? "" : primaryDocument))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ");
        if (normalizedForm.equals("SC TO-I") || normalizedForm.equals("SC TO-T")) {
            return new SecSignal("TENDER_OFFER", "Form " + normalizedForm + " is a tender offer filing.");
        }
        if (normalizedForm.equals("SC 14D9")) {
            return new SecSignal("TENDER_OFFER_RESPONSE", "Form SC 14D9 is target-company tender offer response material.");
        }
        if (normalizedForm.equals("DEFM14A")) {
            return new SecSignal("DEFINITIVE_PROXY", "Form DEFM14A can contain definitive merger proxy material.");
        }
        if (normalizedForm.equals("PREM14A")) {
            return new SecSignal("PRELIMINARY_PROXY", "Form PREM14A can contain preliminary merger proxy material.");
        }
        if (normalizedForm.equals("425") || normalizedForm.equals("S-4")) {
            return new SecSignal("BUSINESS_COMBINATION", "Form " + normalizedForm + " can indicate merger or business combination communications.");
        }
        if (containsAny(text, "merger agreement", "acquisition agreement")) {
            return new SecSignal("MERGER_AGREEMENT", "Matched merger/acquisition agreement language.");
        }
        if (containsAny(text, "going private", "go-private")) {
            return new SecSignal("GOING_PRIVATE", "Matched going-private language.");
        }
        if (containsAny(text, "business combination")) {
            return new SecSignal("BUSINESS_COMBINATION", "Matched business combination language.");
        }
        if (containsAny(text, "offer to purchase", "tender offer")) {
            return new SecSignal("TENDER_OFFER", "Matched offer-to-purchase/tender-offer language.");
        }
        return new SecSignal("WATCHLIST_FORM", "Interesting SEC form for watchlist review.");
    }

    boolean isInterestingForm(String form) {
        return INTERESTING_FORMS.contains(normalizeForm(form));
    }

    static String padCik(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.isBlank()) {
            throw new IllegalArgumentException("CIK is blank");
        }
        if (digits.length() > 10) {
            throw new IllegalArgumentException("CIK has more than 10 digits: " + value);
        }
        return "0".repeat(10 - digits.length()) + digits;
    }

    private SecFilingEntity toEntity(String companyName, SecSubmissionFiling filing) {
        return new SecFilingEntity(
                filing.cik(),
                companyName,
                normalizeForm(filing.form()),
                filing.filingDate(),
                filing.accessionNumber(),
                filing.primaryDocument(),
                filing.filingUrl(),
                filing.signal().signalType(),
                filing.signal().signalReason()
        );
    }

    private String filingUrl(String cik, String accessionNumber, String primaryDocument) {
        String cikNoLeadingZeros = Long.toString(Long.parseLong(cik));
        String accessionNoDashes = accessionNumber.replace("-", "");
        return "https://www.sec.gov/Archives/edgar/data/"
                + cikNoLeadingZeros + "/" + accessionNoDashes + "/" + primaryDocument;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private String normalizeForm(String form) {
        return form == null ? "" : form.trim().toUpperCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    public record SecScanSummary(
            boolean enabled,
            int watchlistCount,
            int fetchedFilings,
            int matchedFilings,
            int savedFilings,
            int duplicatesSkipped,
            String message
    ) {
    }

    public record SecStatus(
            boolean enabled,
            int watchlistCount,
            int maxFilingsPerCik,
            long savedFilings
    ) {
    }

    record SecSubmissions(String cik, String companyName, List<SecSubmissionFiling> filings) {
    }

    record SecSubmissionFiling(
            String cik,
            String form,
            LocalDate filingDate,
            String accessionNumber,
            String primaryDocument,
            String filingUrl,
            SecSignal signal
    ) {
    }

    record SecSignal(String signalType, String signalReason) {
    }
}
