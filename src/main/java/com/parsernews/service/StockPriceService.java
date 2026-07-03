package com.parsernews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockPriceService {
    private static final String YAHOO_BASE = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final Duration CACHE_TTL_DAILY   = Duration.ofMinutes(5);
    private static final Duration CACHE_TTL_INTRADAY = Duration.ofMinutes(1);
    private static final Set<String> INTRADAY_INTERVALS = Set.of("1m","2m","5m","15m","30m","60m","90m");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId MARKET_TZ = ZoneId.of("America/New_York");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CachedEntry<PriceResult>> priceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedEntry<PriceHistory>> historyCache = new ConcurrentHashMap<>();

    public StockPriceService(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = objectMapper;
    }

    public Optional<PriceResult> currentPrice(String ticker) {
        if (isUnknown(ticker)) return Optional.empty();
        CachedEntry<PriceResult> cached = priceCache.get(ticker);
        if (cached != null && !cached.isStale(false)) return Optional.ofNullable(cached.value);
        Optional<PriceResult> result = fetchPrice(ticker);
        priceCache.put(ticker, new CachedEntry<>(result.orElse(null)));
        return result;
    }

    public Optional<PriceHistory> history(String ticker, String range, String interval) {
        if (isUnknown(ticker)) return Optional.empty();
        String resolvedInterval = resolveInterval(range, interval);
        String cacheKey = ticker + ":" + range + ":" + resolvedInterval;
        boolean intraday = INTRADAY_INTERVALS.contains(resolvedInterval);
        CachedEntry<PriceHistory> cached = historyCache.get(cacheKey);
        if (cached != null && !cached.isStale(intraday)) return Optional.ofNullable(cached.value);
        Optional<PriceHistory> result = fetchHistory(ticker, range, resolvedInterval);
        historyCache.put(cacheKey, new CachedEntry<>(result.orElse(null)));
        return result;
    }

    private String resolveInterval(String range, String interval) {
        if (interval != null && !interval.isBlank()) return interval;
        return (range.endsWith("mo") || range.endsWith("y")) ? "1d" : "5m";
    }

    private Optional<PriceResult> fetchPrice(String ticker) {
        try {
            JsonNode meta = fetchMeta(ticker, "1d");
            if (meta == null) return Optional.empty();
            double price = meta.path("regularMarketPrice").asDouble(0);
            // Yahoo's chart meta omits "previousClose" and instead provides "chartPreviousClose";
            // without this fallback prevClose defaults to price and every change reads 0.0%.
            double prevClose = meta.path("previousClose")
                    .asDouble(meta.path("chartPreviousClose").asDouble(price));
            double changePct = prevClose > 0 ? (price - prevClose) / prevClose * 100 : 0;
            return Optional.of(new PriceResult(
                    ticker.toUpperCase(Locale.ROOT),
                    meta.path("shortName").asText(ticker),
                    price,
                    meta.path("currency").asText("USD"),
                    changePct
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<PriceHistory> fetchHistory(String ticker, String range, String interval) {
        try {
            boolean intraday = INTRADAY_INTERVALS.contains(interval);
            String url = YAHOO_BASE + ticker + "?interval=" + interval + "&range=" + range + "&includePrePost=false";
            String body = get(url);
            if (body == null) return Optional.empty();

            JsonNode root = objectMapper.readTree(body);
            JsonNode resultNode = root.path("chart").path("result");
            if (!resultNode.isArray() || resultNode.isEmpty()) return Optional.empty();
            JsonNode result = resultNode.get(0);

            JsonNode meta = result.path("meta");
            JsonNode timestamps = result.path("timestamp");
            JsonNode closes = result.path("indicators").path("quote").get(0).path("close");

            if (!timestamps.isArray() || !closes.isArray()) return Optional.empty();

            List<PricePoint> points = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                long ts = timestamps.get(i).asLong(0);
                JsonNode closeNode = closes.get(i);
                if (ts == 0 || closeNode == null || closeNode.isNull()) continue;
                if (intraday) {
                    var dt = Instant.ofEpochSecond(ts).atZone(MARKET_TZ);
                    String label = dt.format(TIME_FMT);
                    LocalDate date = dt.toLocalDate();
                    points.add(new PricePoint(date, closeNode.asDouble(), ts, label));
                } else {
                    LocalDate date = Instant.ofEpochSecond(ts).atZone(ZoneOffset.UTC).toLocalDate();
                    points.add(new PricePoint(date, closeNode.asDouble(), ts, date.toString()));
                }
            }

            return Optional.of(new PriceHistory(
                    ticker.toUpperCase(Locale.ROOT),
                    meta.path("shortName").asText(ticker),
                    meta.path("currency").asText("USD"),
                    points
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private JsonNode fetchMeta(String ticker, String range) throws Exception {
        String url = YAHOO_BASE + ticker + "?interval=1d&range=" + range + "&includePrePost=false";
        String body = get(url);
        if (body == null) return null;
        JsonNode root = objectMapper.readTree(body);
        JsonNode resultNode = root.path("chart").path("result");
        if (!resultNode.isArray() || resultNode.isEmpty()) return null;
        return resultNode.get(0).path("meta");
    }

    private String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (compatible; ParserNews/1.0)")
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isUnknown(String ticker) {
        return ticker == null || ticker.isBlank() || "UNKNOWN".equalsIgnoreCase(ticker);
    }

    public record PriceResult(
            String ticker,
            String shortName,
            double price,
            String currency,
            double changePct
    ) {
        public String formatted() {
            String sign = changePct >= 0 ? "+" : "";
            return String.format("%.2f %s (%s%.1f%%)", price, currency, sign, changePct);
        }
    }

    public record PriceHistory(
            String ticker,
            String shortName,
            String currency,
            List<PricePoint> points
    ) {
    }

    public record PricePoint(LocalDate date, double close, long ts, String label) {
    }

    private static class CachedEntry<T> {
        final T value;
        final Instant fetchedAt = Instant.now();

        CachedEntry(T value) {
            this.value = value;
        }

        boolean isStale(boolean intraday) {
            Duration ttl = intraday ? CACHE_TTL_INTRADAY : CACHE_TTL_DAILY;
            return Instant.now().isAfter(fetchedAt.plus(ttl));
        }
    }
}
