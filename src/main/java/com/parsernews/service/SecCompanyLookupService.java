package com.parsernews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.persistence.CompanyMatchConfidence;
import com.parsernews.persistence.SecWatchlistCompanyEntity;
import com.parsernews.persistence.SecWatchlistCompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class SecCompanyLookupService {
    private static final int DEFAULT_LIMIT = 10;
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final SecCompanyTickerClient client;
    private final SecWatchlistCompanyRepository watchlistRepository;
    private final ObjectMapper objectMapper;
    private volatile List<CompanyTickerEntry> cachedEntries = List.of();
    private volatile Instant cacheLoadedAt = Instant.EPOCH;

    public SecCompanyLookupService(
            SecCompanyTickerClient client,
            SecWatchlistCompanyRepository watchlistRepository,
            ObjectMapper objectMapper
    ) {
        this.client = client;
        this.watchlistRepository = watchlistRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SecCompanyLookupResult> search(String query) {
        String normalizedQuery = normalizeQuery(query);
        List<SecWatchlistCompanyEntity> watchlist = watchlistRepository.findAll();
        return loadEntries().stream()
                .map(entry -> match(entry, normalizedQuery, watchlist))
                .filter(result -> result.matchType() != null)
                .sorted(Comparator.comparing(SecCompanyLookupResult::matchRank)
                        .thenComparing(SecCompanyLookupResult::ticker)
                        .thenComparing(SecCompanyLookupResult::companyName))
                .limit(DEFAULT_LIMIT)
                .toList();
    }

    public Optional<CompanyLookupMatch> findBestMatch(String explicitTicker, String companyName) {
        String ticker = normalizeOptionalTicker(explicitTicker);
        List<CompanyTickerEntry> entries = loadEntries();
        if (ticker != null) {
            Optional<CompanyTickerEntry> tickerMatch = entries.stream()
                    .filter(entry -> entry.ticker().equalsIgnoreCase(ticker))
                    .findFirst();
            if (tickerMatch.isPresent()) {
                return tickerMatch.map(entry -> toCompanyMatch(entry, CompanyMatchConfidence.EXACT_TICKER));
            }
        }

        String normalizedCompanyName = normalizeCompanyName(companyName);
        if (normalizedCompanyName == null || isGenericCompanyName(normalizedCompanyName)) {
            return Optional.empty();
        }

        Optional<CompanyTickerEntry> exactName = entries.stream()
                .filter(entry -> normalizeCompanyName(entry.companyName()).equals(normalizedCompanyName))
                .findFirst();
        if (exactName.isPresent()) {
            return exactName.map(entry -> toCompanyMatch(entry, CompanyMatchConfidence.EXACT_NAME));
        }

        if (normalizedCompanyName.length() < 12 || normalizedCompanyName.split(" ").length < 2) {
            return Optional.empty();
        }
        return entries.stream()
                .filter(entry -> {
                    String entryName = normalizeCompanyName(entry.companyName());
                    return entryName.contains(normalizedCompanyName) || normalizedCompanyName.contains(entryName);
                })
                .filter(entry -> normalizeCompanyName(entry.companyName()).length() >= 12)
                .findFirst()
                .map(entry -> toCompanyMatch(entry, CompanyMatchConfidence.PARTIAL_NAME));
    }

    private SecCompanyLookupResult match(
            CompanyTickerEntry entry,
            String normalizedQuery,
            List<SecWatchlistCompanyEntity> watchlist
    ) {
        MatchType matchType = null;
        String ticker = entry.ticker().toUpperCase(Locale.ROOT);
        String companyName = entry.companyName();
        if (ticker.equals(normalizedQuery)) {
            matchType = MatchType.EXACT_TICKER;
        } else if (ticker.contains(normalizedQuery)) {
            matchType = MatchType.PARTIAL_TICKER;
        } else if (companyName.toLowerCase(Locale.ROOT).contains(normalizedQuery.toLowerCase(Locale.ROOT))) {
            matchType = MatchType.PARTIAL_NAME;
        }
        SecWatchlistCompanyEntity existing = watchlist.stream()
                .filter(item -> item.getCik().equals(entry.cik()))
                .findFirst()
                .orElse(null);
        return new SecCompanyLookupResult(
                entry.cik(),
                ticker,
                companyName,
                matchType,
                existing != null,
                existing != null && existing.isEnabled()
        );
    }

    private List<CompanyTickerEntry> loadEntries() {
        Instant now = Instant.now();
        List<CompanyTickerEntry> current = cachedEntries;
        if (!current.isEmpty() && cacheLoadedAt.plus(CACHE_TTL).isAfter(now)) {
            return current;
        }
        synchronized (this) {
            if (!cachedEntries.isEmpty() && cacheLoadedAt.plus(CACHE_TTL).isAfter(Instant.now())) {
                return cachedEntries;
            }
            try {
                cachedEntries = parse(client.fetchCompanyTickerJson());
                cacheLoadedAt = Instant.now();
                return cachedEntries;
            } catch (IOException exception) {
                throw new SecCompanyLookupException("SEC company lookup is temporarily unavailable", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SecCompanyLookupException("SEC company lookup was interrupted", exception);
            }
        }
    }

    private List<CompanyTickerEntry> parse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        if (!root.isObject()) {
            return List.of();
        }
        List<CompanyTickerEntry> entries = new ArrayList<>();
        root.properties().forEach(field -> {
            JsonNode node = field.getValue();
            CompanyTickerEntry entry = new CompanyTickerEntry(
                    SecWatchlistManagerService.normalizeCik(node.path("cik_str").asText()),
                    node.path("ticker").asText("").trim(),
                    node.path("title").asText("").trim()
            );
            if (!entry.cik().isBlank() && !entry.ticker().isBlank() && !entry.companyName().isBlank()) {
                entries.add(entry);
            }
        });
        return entries;
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Lookup query is required");
        }
        String normalized = query.trim();
        if (normalized.length() < 2) {
            throw new IllegalArgumentException("Lookup query must contain at least 2 characters");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private CompanyLookupMatch toCompanyMatch(CompanyTickerEntry entry, CompanyMatchConfidence confidence) {
        return new CompanyLookupMatch(
                entry.cik(),
                entry.ticker().toUpperCase(Locale.ROOT),
                entry.companyName(),
                confidence
        );
    }

    private String normalizeOptionalTicker(String value) {
        if (value == null || value.isBlank() || "UNKNOWN".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z][A-Z0-9.-]{0,9}") ? normalized : null;
    }

    private String normalizeCompanyName(String value) {
        if (value == null || value.isBlank() || "UNKNOWN".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\b(incorporated|inc|corp|corporation|co|company|ltd|limited|plc|holdings|holding|group|sa|nv|ag|common|stock|class|ordinary|shares)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isGenericCompanyName(String normalized) {
        return normalized.length() < 4
                || List.of("the", "company", "target", "buyer", "business", "assets", "shareholders").contains(normalized);
    }

    private record CompanyTickerEntry(String cik, String ticker, String companyName) {
    }

    public record CompanyLookupMatch(
            String cik,
            String ticker,
            String companyName,
            CompanyMatchConfidence confidence
    ) {
        public boolean publicCompanyEvidence() {
            return confidence == CompanyMatchConfidence.EXACT_TICKER || confidence == CompanyMatchConfidence.EXACT_NAME;
        }
    }

    public enum MatchType {
        EXACT_TICKER,
        PARTIAL_TICKER,
        PARTIAL_NAME
    }

    public record SecCompanyLookupResult(
            String cik,
            String ticker,
            String companyName,
            MatchType matchType,
            boolean alreadyInWatchlist,
            boolean enabledInWatchlist
    ) {
        int matchRank() {
            return switch (matchType) {
                case EXACT_TICKER -> 0;
                case PARTIAL_TICKER -> 1;
                case PARTIAL_NAME -> 2;
            };
        }
    }

    public static class SecCompanyLookupException extends RuntimeException {
        public SecCompanyLookupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
