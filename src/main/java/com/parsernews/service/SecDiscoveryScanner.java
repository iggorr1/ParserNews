package com.parsernews.service;

import com.parsernews.config.SecDiscoverySettings;
import com.parsernews.persistence.SecDiscoveryRunEntity;
import com.parsernews.persistence.SecDiscoveryRunRepository;
import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.persistence.SecSignalPriority;
import com.parsernews.persistence.SecSignalType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SecDiscoveryScanner {
    private static final Pattern ARCHIVE_URL = Pattern.compile(
            "/Archives/edgar/data/(\\d+)/(\\d+)/([^/?#]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CIK_IN_TITLE = Pattern.compile("\\((\\d{1,10})\\)");

    private final SecDiscoverySettings settings;
    private final SecCurrentFilingsClient currentFilingsClient;
    private final SecFilingRepository filingRepository;
    private final SecDiscoveryRunRepository runRepository;
    private final SecFilingDocumentFetcher documentFetcher;

    public SecDiscoveryScanner(
            SecDiscoverySettings settings,
            SecCurrentFilingsClient currentFilingsClient,
            SecFilingRepository filingRepository,
            SecDiscoveryRunRepository runRepository,
            SecFilingDocumentFetcher documentFetcher
    ) {
        this.settings = settings;
        this.currentFilingsClient = currentFilingsClient;
        this.filingRepository = filingRepository;
        this.runRepository = runRepository;
        this.documentFetcher = documentFetcher;
    }

    @Transactional
    public SecDiscoverySummary scan() {
        Instant startedAt = Instant.now();
        if (!settings.enabled()) {
            return new SecDiscoverySummary(
                    false,
                    false,
                    startedAt,
                    Instant.now(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of("SEC discovery is disabled."),
                    0
            );
        }

        SecDiscoveryRunEntity run = runRepository.save(new SecDiscoveryRunEntity(startedAt));
        int scanned = 0;
        int saved = 0;
        int duplicates = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenAccessions = new LinkedHashSet<>();
        int maxPerForm = Math.max(1, settings.maxFilingsPerRun());

        try {
            // Phase 1: fetch every form's current-filings feed concurrently (bounded, no DB work).
            // EDGAR is the primary, near-real-time source, so cutting the sequential fetch chain
            // shortens time-to-detection for SEC-sourced deals. DB writes stay single-threaded below.
            Map<String, String> atomByForm = fetchAtomsInParallel(settings.formList(), maxPerForm, errors);

            // Phase 2a: parse every fetched feed once.
            Map<String, List<DiscoveredSecFiling>> parsedByForm = new LinkedHashMap<>();
            List<String> candidateAccessions = new ArrayList<>();
            for (String form : settings.formList()) {
                String atom = atomByForm.get(form);
                if (atom == null) {
                    continue; // fetch failed — already recorded in errors
                }
                try {
                    List<DiscoveredSecFiling> filings = parseCurrentFeed(form, atom);
                    parsedByForm.put(form, filings);
                    for (DiscoveredSecFiling filing : filings) {
                        candidateAccessions.add(filing.accessionNumber());
                    }
                } catch (RuntimeException exception) {
                    errors.add(form + ": " + exception.getMessage());
                }
            }

            // Phase 2b: one batch existence query instead of one findByAccessionNumber per filing —
            // EDGAR returns the same recent filings every 20s poll, so this avoids ~100 wasted lookups.
            Set<String> existingAccessions = candidateAccessions.isEmpty()
                    ? Set.of()
                    : filingRepository.findExistingAccessionNumbers(candidateAccessions);

            // Phase 2c: persist new filings, honoring the total per-run cap.
            for (String form : settings.formList()) {
                if (scanned >= settings.maxFilingsPerRun()) {
                    break;
                }
                List<DiscoveredSecFiling> filings = parsedByForm.get(form);
                if (filings == null) {
                    continue;
                }
                for (DiscoveredSecFiling filing : filings) {
                    if (scanned >= settings.maxFilingsPerRun()) {
                        break;
                    }
                    if (!isFormAllowed(filing.form())) {
                        skipped++;
                        continue;
                    }
                    scanned++;
                    if (!seenAccessions.add(filing.accessionNumber())) {
                        duplicates++;
                        continue;
                    }
                    if (existingAccessions.contains(filing.accessionNumber())) {
                        duplicates++;
                        continue;
                    }
                    SecMetadataSignal signal = detectMetadataSignal(filing);
                    SecFilingEntity entity = toEntity(filing, signal);
                    entity.updateSecSignal(signal.type(), signal.priority(), signal.summary(), signal.warning());
                    if (isAmendment(filing.form())) {
                        entity.markAsAmendment();
                    }
                    SecFilingEntity savedEntity = filingRepository.save(entity);
                    saved++;
                    if (settings.fetchPrimaryDocument()) {
                        documentFetcher.fetchDocument(savedEntity.getId());
                    }
                }
            }
            Instant finishedAt = Instant.now();
            String status = errors.isEmpty() ? "SUCCESS" : saved > 0 ? "PARTIAL_SUCCESS" : "FAILED";
            run.finish(status, scanned, saved, duplicates, saved, skipped, errors.size(), joinErrors(errors));
            return new SecDiscoverySummary(
                    true,
                    true,
                    startedAt,
                    finishedAt,
                    scanned,
                    saved,
                    duplicates,
                    saved,
                    skipped,
                    errors,
                    Duration.between(startedAt, finishedAt).toMillis()
            );
        } catch (RuntimeException exception) {
            Instant finishedAt = Instant.now();
            errors.add(exception.getMessage());
            run.finish("FAILED", scanned, saved, duplicates, saved, skipped, errors.size(), joinErrors(errors));
            return new SecDiscoverySummary(
                    true,
                    false,
                    startedAt,
                    finishedAt,
                    scanned,
                    saved,
                    duplicates,
                    saved,
                    skipped,
                    errors,
                    Duration.between(startedAt, finishedAt).toMillis()
            );
        }
    }

    @Transactional(readOnly = true)
    public SecDiscoveryStatus status() {
        Optional<SecDiscoveryRunEntity> latest = runRepository.findTopByOrderByStartedAtDesc();
        return new SecDiscoveryStatus(
                settings.enabled(),
                settings.scheduler().enabled(),
                settings.formList(),
                settings.maxFilingsPerRun(),
                settings.fetchPrimaryDocument(),
                latest.map(SecDiscoveryRunEntity::getStartedAt).orElse(null),
                latest.map(SecDiscoveryRunEntity::getStatus).orElse(null),
                latest.map(this::summaryFromRun).orElse(null),
                discoveryWarning()
        );
    }

    // Bounded so we stay well under SEC's ~10 requests/second fair-access limit.
    private static final int SEC_FETCH_CONCURRENCY = 5;

    /**
     * Fetches each form's current-filings atom feed in parallel. Returns a map of form -> atom XML
     * for the forms that fetched successfully; failures are appended to {@code errors}. Only the
     * network fetch runs concurrently — the caller persists results single-threaded.
     */
    private Map<String, String> fetchAtomsInParallel(List<String> forms, int count, List<String> errors) {
        if (forms.isEmpty()) {
            return Map.of();
        }
        int poolSize = Math.min(SEC_FETCH_CONCURRENCY, forms.size());
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        try {
            Map<String, Future<String>> futures = new LinkedHashMap<>();
            for (String form : forms) {
                futures.put(form, pool.submit(() -> currentFilingsClient.fetchCurrentFilingsAtom(form, count)));
            }
            Map<String, String> atomByForm = new LinkedHashMap<>();
            for (Map.Entry<String, Future<String>> entry : futures.entrySet()) {
                String form = entry.getKey();
                try {
                    atomByForm.put(form, entry.getValue().get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    errors.add(form + ": interrupted");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    errors.add(form + ": " + (cause != null ? cause.getMessage() : exception.getMessage()));
                }
            }
            return atomByForm;
        } finally {
            pool.shutdown();
        }
    }

    List<DiscoveredSecFiling> parseCurrentFeed(String fallbackForm, String atomXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(atomXml)));
            NodeList entries = document.getElementsByTagName("entry");
            List<DiscoveredSecFiling> filings = new ArrayList<>();
            for (int index = 0; index < entries.getLength(); index++) {
                Element entry = (Element) entries.item(index);
                String title = childText(entry, "title");
                String updated = childText(entry, "updated");
                String href = firstLinkHref(entry);
                ArchiveParts archive = parseArchiveParts(href);
                if (archive == null) {
                    continue;
                }
                String companyName = companyFromTitle(title, fallbackForm);
                filings.add(new DiscoveredSecFiling(
                        archive.cik(),
                        companyName,
                        normalizeForm(fallbackForm),
                        parseFilingDate(updated),
                        archive.accessionNumber(),
                        archive.primaryDocument(),
                        href,
                        title
                ));
            }
            return filings;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot parse SEC current feed: " + exception.getMessage(), exception);
        }
    }

    private SecFilingEntity toEntity(DiscoveredSecFiling filing, SecMetadataSignal signal) {
        return new SecFilingEntity(
                filing.cik(),
                filing.companyName(),
                normalizeForm(filing.form()),
                filing.filingDate(),
                filing.accessionNumber(),
                filing.primaryDocument(),
                filing.filingUrl(),
                signal.type().name(),
                signal.summary()
        );
    }

    private SecMetadataSignal detectMetadataSignal(DiscoveredSecFiling filing) {
        String form = baseForm(filing.form());
        String text = normalize(filing.title() + " " + filing.primaryDocument());
        String amendmentWarning = isAmendment(filing.form())
                ? "Amendment filing — review for updated deal terms (price, extension, termination)."
                : null;
        if (form.equals("SC TO-I") || form.equals("SC TO-T") || containsAny(text, "tender offer", "offer to purchase")) {
            return new SecMetadataSignal(SecSignalType.TENDER_OFFER, SecSignalPriority.HIGH, "Discovery filing indicates tender offer activity.", amendmentWarning);
        }
        if (containsAny(text, "going private", "go private")) {
            return new SecMetadataSignal(SecSignalType.GOING_PRIVATE, SecSignalPriority.HIGH, "Discovery filing mentions going-private language.", amendmentWarning);
        }
        if (containsAny(text, "merger agreement", "agreement and plan of merger", "acquisition agreement")) {
            return new SecMetadataSignal(SecSignalType.MERGER_AGREEMENT, SecSignalPriority.HIGH, "Discovery filing mentions merger/acquisition agreement language.", amendmentWarning);
        }
        if (form.equals("DEFM14A") || form.equals("PREM14A") || form.equals("SC 14D9")) {
            String warning = amendmentWarning != null ? amendmentWarning : "Review timing and transaction context.";
            return new SecMetadataSignal(SecSignalType.DEFINITIVE_PROXY, SecSignalPriority.MEDIUM, "Discovery filing form can contain M&A proxy or response material.", warning);
        }
        if (form.equals("425") || form.equals("S-4") || containsAny(text, "business combination")) {
            return new SecMetadataSignal(SecSignalType.BUSINESS_COMBINATION, SecSignalPriority.MEDIUM, "Discovery filing form can indicate merger/business-combination communications.", amendmentWarning);
        }
        return new SecMetadataSignal(SecSignalType.ROUTINE_FILING, SecSignalPriority.LOW, "Discovery filing matched configured SEC form.", amendmentWarning != null ? amendmentWarning : "Metadata-only signal; document review may be needed.");
    }

    private String childText(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }

    private String firstLinkHref(Element entry) {
        NodeList links = entry.getElementsByTagName("link");
        for (int index = 0; index < links.getLength(); index++) {
            Element link = (Element) links.item(index);
            String href = link.getAttribute("href");
            if (href != null && !href.isBlank()) {
                return href;
            }
        }
        return "";
    }

    private ArchiveParts parseArchiveParts(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        Matcher matcher = ARCHIVE_URL.matcher(URI.create(href).getPath());
        if (!matcher.find()) {
            return null;
        }
        String cik = SecWatchlistScanner.padCik(matcher.group(1));
        String accessionNoDashes = matcher.group(2);
        return new ArchiveParts(cik, formatAccession(accessionNoDashes), matcher.group(3));
    }

    private String formatAccession(String value) {
        if (value != null && value.matches("\\d{18}")) {
            return value.substring(0, 10) + "-" + value.substring(10, 12) + "-" + value.substring(12);
        }
        return value == null ? "" : value;
    }

    private String companyFromTitle(String title, String fallbackForm) {
        if (title == null || title.isBlank()) {
            return "UNKNOWN";
        }
        String cleaned = title.replaceFirst("(?i)^" + Pattern.quote(fallbackForm) + "\\s*-\\s*", "").trim();
        Matcher matcher = CIK_IN_TITLE.matcher(cleaned);
        if (matcher.find()) {
            cleaned = cleaned.substring(0, matcher.start()).trim();
        }
        return cleaned.isBlank() ? "UNKNOWN" : cleaned;
    }

    private LocalDate parseFilingDate(String updated) {
        if (updated == null || updated.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(updated).toLocalDate();
        } catch (RuntimeException ignored) {
            return LocalDate.parse(updated.substring(0, Math.min(10, updated.length())));
        }
    }

    private SecDiscoverySummary summaryFromRun(SecDiscoveryRunEntity run) {
        return new SecDiscoverySummary(
                settings.enabled(),
                "SUCCESS".equals(run.getStatus()) || "PARTIAL_SUCCESS".equals(run.getStatus()),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getScannedCount(),
                run.getNewCount(),
                run.getDuplicateCount(),
                run.getCreatedOrUpdatedGroupCount(),
                run.getSkippedCount(),
                run.getErrorMessage() == null || run.getErrorMessage().isBlank() ? List.of() : List.of(run.getErrorMessage()),
                run.getFinishedAt() == null ? 0 : Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis()
        );
    }

    private String discoveryWarning() {
        if (!settings.enabled()) {
            return "SEC discovery is disabled.";
        }
        if (settings.formList().isEmpty()) {
            return "SEC discovery has no configured forms.";
        }
        return null;
    }

    private String normalizeForm(String form) {
        return form == null ? "" : form.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isAmendment(String form) {
        return normalizeForm(form).endsWith("/A");
    }

    private String baseForm(String form) {
        String normalized = normalizeForm(form);
        return normalized.endsWith("/A") ? normalized.substring(0, normalized.length() - 2) : normalized;
    }

    private boolean isFormAllowed(String form) {
        String normalized = normalizeForm(form);
        return settings.formList().contains(normalized) || settings.formList().contains(baseForm(form));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9$%.]+", " ");
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private String joinErrors(List<String> errors) {
        return errors == null || errors.isEmpty() ? null : String.join("; ", errors);
    }

    public record SecDiscoverySummary(
            boolean enabled,
            boolean success,
            Instant startedAt,
            Instant finishedAt,
            int scannedFilings,
            int newFilings,
            int duplicateFilings,
            int createdOrUpdatedDealGroups,
            int skippedFilings,
            List<String> errors,
            long durationMs
    ) {
    }

    public record SecDiscoveryStatus(
            boolean enabled,
            boolean schedulerEnabled,
            List<String> forms,
            int maxFilingsPerRun,
            boolean fetchPrimaryDocument,
            Instant lastRunAt,
            String lastRunStatus,
            SecDiscoverySummary lastRunSummary,
            String warning
    ) {
    }

    record DiscoveredSecFiling(
            String cik,
            String companyName,
            String form,
            LocalDate filingDate,
            String accessionNumber,
            String primaryDocument,
            String filingUrl,
            String title
    ) {
    }

    private record ArchiveParts(String cik, String accessionNumber, String primaryDocument) {
    }

    private record SecMetadataSignal(
            SecSignalType type,
            SecSignalPriority priority,
            String summary,
            String warning
    ) {
    }
}
