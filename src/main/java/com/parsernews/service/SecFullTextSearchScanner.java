package com.parsernews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.config.SecFullTextSearchSettings;
import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.persistence.SecSignalPriority;
import com.parsernews.persistence.SecSignalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers M&A deals by SEC EDGAR full-text search (EFTS): searches filing content for deal
 * phrases and persists matches as {@link SecFilingEntity} so they flow into the existing deal
 * grouping and dispatch. Catches early 8-K announcements the form-based getcurrent scanner misses.
 */
@Service
public class SecFullTextSearchScanner {
    private static final Logger log = LoggerFactory.getLogger(SecFullTextSearchScanner.class);
    // "Company Name, Inc.  (TICKER)  (CIK 0000000000)" -> company, ticker, cik
    private static final Pattern DISPLAY_NAME = Pattern.compile(
            "^(.*?)\\s*(?:\\(([A-Z0-9.\\-]+)\\)\\s*)?\\(CIK\\s*(\\d+)\\)", Pattern.CASE_INSENSITIVE);

    private final SecFullTextSearchSettings settings;
    private final SecFullTextSearchClient client;
    private final SecFilingRepository filingRepository;
    private final ObjectMapper objectMapper;

    private volatile java.time.Instant lastRunAt;
    private volatile FtsSummary lastSummary;

    public SecFullTextSearchScanner(
            SecFullTextSearchSettings settings,
            SecFullTextSearchClient client,
            SecFilingRepository filingRepository,
            ObjectMapper objectMapper
    ) {
        this.settings = settings;
        this.client = client;
        this.filingRepository = filingRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FtsSummary scan() {
        if (!settings.enabled()) {
            return new FtsSummary(false, 0, 0, 0, List.of("SEC full-text search is disabled."));
        }
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(settings.lookbackDays());
        List<String> errors = new ArrayList<>();

        // Dedup across queries by accession (the same 8-K can match several phrases).
        Map<String, DiscoveredFtsFiling> byAccession = new LinkedHashMap<>();
        for (String query : settings.queryList()) {
            try {
                String json = client.search(query, settings.forms(), start, end);
                for (DiscoveredFtsFiling filing : parseHits(json, query)) {
                    byAccession.putIfAbsent(filing.accessionNumber(), filing);
                }
            } catch (java.io.IOException | RuntimeException exception) {
                errors.add(query + ": " + exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                errors.add(query + ": interrupted");
                break;
            }
            sleepConservatively();
        }

        int found = byAccession.size();
        int created = 0;
        int upgraded = 0;
        for (DiscoveredFtsFiling filing : byAccession.values()) {
            var existing = filingRepository.findByAccessionNumber(filing.accessionNumber());
            if (existing.isPresent()) {
                // getcurrent grabs 8-Ks by form only (title-only) and marks most as generic/LOW.
                // EFTS confirmed by content that this one is an M&A deal — upgrade it to HIGH so it
                // reaches dispatch, instead of skipping it as a duplicate.
                SecFilingEntity entity = existing.get();
                boolean changed = false;
                if (entity.getTicker() == null && filing.ticker() != null) {
                    entity.setTicker(filing.ticker());
                    changed = true;
                }
                if (entity.getSecSignalPriority() != SecSignalPriority.HIGH) {
                    entity.updateSecSignal(filing.signalType(), SecSignalPriority.HIGH, filing.summary(), null);
                    changed = true;
                    upgraded++;
                }
                if (changed) {
                    filingRepository.save(entity);
                }
                continue;
            }
            SecFilingEntity entity = new SecFilingEntity(
                    filing.cik(),
                    filing.companyName(),
                    filing.form(),
                    filing.filingDate(),
                    filing.accessionNumber(),
                    filing.primaryDocument(),
                    filing.filingUrl(),
                    filing.signalType().name(),
                    filing.summary());
            entity.setTicker(filing.ticker());
            entity.updateSecSignal(filing.signalType(), SecSignalPriority.HIGH, filing.summary(), null);
            filingRepository.save(entity);
            created++;
        }
        if (created > 0 || upgraded > 0 || !errors.isEmpty()) {
            log.info("EFTS scan — found: {}, new: {}, upgraded: {}, errors: {}",
                    found, created, upgraded, errors.size());
        }
        FtsSummary summary = new FtsSummary(true, found, created, upgraded, errors);
        lastRunAt = java.time.Instant.now();
        lastSummary = summary;
        return summary;
    }

    /** Last EFTS run for observability (null timestamp until the first run). */
    public FtsStatus status() {
        return new FtsStatus(settings.enabled(), lastRunAt, lastSummary);
    }

    public record FtsStatus(boolean enabled, java.time.Instant lastRunAt, FtsSummary lastSummary) {
    }

    List<DiscoveredFtsFiling> parseHits(String json, String query) {
        List<DiscoveredFtsFiling> results = new ArrayList<>();
        JsonNode hits;
        try {
            hits = objectMapper.readTree(json).path("hits").path("hits");
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot parse EFTS response: " + exception.getMessage(), exception);
        }
        SecSignalType signalType = signalFor(query);
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            String id = hit.path("_id").asText("");
            String accession = source.path("adsh").asText(id.contains(":") ? id.substring(0, id.indexOf(':')) : "");
            if (accession.isBlank()) {
                continue;
            }
            String primaryDocument = id.contains(":") ? id.substring(id.indexOf(':') + 1) : "";
            String form = source.path("file_type").asText(firstText(source.path("root_forms"), "8-K"));
            String displayName = firstText(source.path("display_names"), "");
            String cik = firstText(source.path("ciks"), "");
            NameParts parts = parseDisplayName(displayName, cik);
            LocalDate filingDate = parseDate(source.path("file_date").asText(""));

            String summary = "EFTS: \"" + query + "\" in " + form
                    + (parts.ticker() != null ? " — " + parts.company() + " (" + parts.ticker() + ")" : "");
            results.add(new DiscoveredFtsFiling(
                    parts.cik(),
                    parts.company(),
                    parts.ticker(),
                    form,
                    filingDate,
                    accession,
                    primaryDocument,
                    buildFilingUrl(parts.cik(), accession),
                    signalType,
                    summary));
        }
        return results;
    }

    private static SecSignalType signalFor(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        if (lower.contains("tender offer")) {
            return SecSignalType.TENDER_OFFER;
        }
        return SecSignalType.MERGER_AGREEMENT;
    }

    private NameParts parseDisplayName(String displayName, String fallbackCik) {
        Matcher matcher = DISPLAY_NAME.matcher(displayName == null ? "" : displayName);
        if (matcher.find()) {
            String company = matcher.group(1) == null ? "Unknown Company" : matcher.group(1).trim();
            String ticker = matcher.group(2);
            String cik = matcher.group(3);
            return new NameParts(company.isBlank() ? "Unknown Company" : company, ticker, cik);
        }
        String company = displayName == null || displayName.isBlank() ? "Unknown Company" : displayName.trim();
        return new NameParts(company, null, fallbackCik);
    }

    private static String firstText(JsonNode arrayNode, String fallback) {
        if (arrayNode != null && arrayNode.isArray() && !arrayNode.isEmpty()) {
            return arrayNode.get(0).asText(fallback);
        }
        return fallback;
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? LocalDate.now() : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return LocalDate.now();
        }
    }

    private static String buildFilingUrl(String cik, String accession) {
        if (cik == null || cik.isBlank() || accession == null || accession.isBlank()) {
            return "https://www.sec.gov/cgi-bin/browse-edgar";
        }
        String cikNoZeros = cik.replaceFirst("^0+", "");
        String accNoDashes = accession.replace("-", "");
        return "https://www.sec.gov/Archives/edgar/data/" + cikNoZeros + "/" + accNoDashes + "/" + accession + "-index.htm";
    }

    private void sleepConservatively() {
        if (settings.requestDelayMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(settings.requestDelayMs());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record NameParts(String company, String ticker, String cik) {
    }

    record DiscoveredFtsFiling(
            String cik,
            String companyName,
            String ticker,
            String form,
            LocalDate filingDate,
            String accessionNumber,
            String primaryDocument,
            String filingUrl,
            SecSignalType signalType,
            String summary
    ) {
    }

    public record FtsSummary(boolean enabled, int found, int created, int upgraded, List<String> errors) {
        /** New filings created plus generic filings upgraded to an M&A signal. */
        public int changed() {
            return created + upgraded;
        }
    }
}
