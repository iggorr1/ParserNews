package com.parsernews.parser;

import com.parsernews.config.RssSettings;
import com.parsernews.model.NewsEvent;
import com.parsernews.service.RssFeedHealthService;
import com.parsernews.service.SecCompanyLookupService;
import com.parsernews.util.ArticleTextCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "scanner.source", havingValue = "rss")
public class RssNewsParser implements NewsSourceParser {
    private static final Logger log = LoggerFactory.getLogger(RssNewsParser.class);
    private static final Pattern EXCHANGE_TICKER = Pattern.compile(
            "(?i)(?:NYSE\\s+American|NYSEAMERICAN|NASDAQ|NYSE|AMEX|OTCQB|OTCQX|OTC|OTCMKTS|TSX|TSXV)\\s*:\\s*([A-Z][A-Z0-9.-]{0,9})"
    );

    private final RssSettings settings;
    private final HttpClient httpClient;
    private final RssFeedHealthService healthService;
    private final SecCompanyLookupService companyLookupService;

    @Autowired
    public RssNewsParser(RssSettings settings, RssFeedHealthService healthService,
                         SecCompanyLookupService companyLookupService) {
        this(settings, healthService, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), companyLookupService);
    }

    RssNewsParser(RssSettings settings, RssFeedHealthService healthService, HttpClient httpClient) {
        this(settings, healthService, httpClient, null);
    }

    RssNewsParser(RssSettings settings, RssFeedHealthService healthService, HttpClient httpClient,
                  SecCompanyLookupService companyLookupService) {
        this.settings = settings;
        this.healthService = healthService;
        this.httpClient = httpClient;
        this.companyLookupService = companyLookupService;
    }

    /**
     * On startup, drop health records for feeds that are no longer in the configured list
     * (e.g. feeds removed from application.properties), so they don't linger as "unhealthy".
     */
    @EventListener(ApplicationReadyEvent.class)
    public void pruneStaleFeedHealth() {
        int pruned = healthService.pruneUnconfigured(settings.urls());
        if (pruned > 0) {
            log.info("Pruned {} health record(s) for unconfigured RSS feed(s)", pruned);
        }
    }

    @Override
    public List<NewsEvent> readNews() {
        List<NewsEvent> events = new ArrayList<>();
        int failedFeeds = 0;

        for (String url : settings.urls()) {
            try {
                List<NewsEvent> feedEvents = readFeed(url);
                events.addAll(feedEvents);
                healthService.recordSuccess(url);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid RSS feed configuration: " + url, exception);
            } catch (IllegalStateException exception) {
                failedFeeds++;
                healthService.recordError(url, exception.getMessage());
                log.warn("Skipped RSS feed (could not be read): {} — {}", url, exception.getMessage());
            }
        }

        if (events.isEmpty() && failedFeeds == settings.urls().size()) {
            throw new IllegalStateException("All configured RSS feeds failed.");
        }
        return events;
    }

    private List<NewsEvent> readFeed(String url) {
        URI uri = URI.create(url);
        validateUri(uri);

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Accept", "application/rss+xml, application/xml, text/xml")
                    .header("User-Agent", userAgentFor(uri))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("RSS feed returned status " + response.statusCode() + ": " + url);
            }

            return parseRss(response.body(), url);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading RSS feed " + url, exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read RSS feed " + url, exception);
        }
    }

    /**
     * SEC (sec.gov) rejects requests (HTTP 403) whose User-Agent lacks a contact email,
     * per its fair-access policy. Route SEC hosts through the shared SEC-compliant UA.
     */
    private String userAgentFor(URI uri) {
        String host = uri.getHost();
        if (host != null && (host.equalsIgnoreCase("sec.gov") || host.toLowerCase(Locale.ROOT).endsWith(".sec.gov"))) {
            return com.parsernews.service.SecHttpUserAgent.VALUE;
        }
        return "ParserNews/1.0 research-news-scanner";
    }

    private void validateUri(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only HTTPS RSS feeds are allowed: " + uri);
        }
    }

    private List<NewsEvent> parseRss(String xml, String feedUrl) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xml)));

        String source = cleanText(textOfFirst(document.getDocumentElement(), "title", ""));
        if (source.isBlank()) {
            source = sourceFromUrl(feedUrl);
        }
        // Support both RSS 2.0 (<item>) and Atom 1.0 (<entry>)
        NodeList items = document.getElementsByTagName("item");
        boolean isAtom = items.getLength() == 0;
        if (isAtom) {
            items = document.getElementsByTagName("entry");
        }
        List<NewsEvent> events = new ArrayList<>();
        int limit = Math.min(items.getLength(), settings.maxItemsPerFeed());

        for (int index = 0; index < limit; index++) {
            Element item = (Element) items.item(index);
            String headline = cleanText(textOfFirst(item, "title", ""));
            String body = cleanText(isAtom
                    ? textOfFirst(item, "summary", textOfFirst(item, "content", ""))
                    : textOfFirst(item, "description", ""));
            String link = isAtom
                    ? atomLink(item, feedUrl)
                    : cleanText(textOfFirst(item, "link", feedUrl));
            String pubDate = isAtom
                    ? textOfFirst(item, "updated", textOfFirst(item, "published", ""))
                    : textOfFirst(item, "pubDate", "");
            String articleBody = fetchFullArticleText(link, headline + " " + body).orElse(body);
            Instant publishedAt = parsePublishedAt(pubDate);
            String companyName = guessCompanyName(headline);
            String ticker = guessTicker(headline + " " + articleBody, companyName);

            if (!headline.isBlank()) {
                events.add(new NewsEvent(
                        ticker,
                        companyName,
                        headline,
                        articleBody,
                        source,
                        link,
                        publishedAt,
                        null,
                        null,
                        null
                ));
            }
        }

        return events;
    }

    private java.util.Optional<String> fetchFullArticleText(String link, String teaserText) {
        if (!settings.fetchFullArticleText() || link == null || link.isBlank() || !shouldFetchArticleText(teaserText)) {
            return java.util.Optional.empty();
        }
        try {
            URI uri = URI.create(link);
            validateUri(uri);
            if (!isWhitelistedArticleHost(uri)) {
                return java.util.Optional.empty();
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Accept", "text/html, application/xhtml+xml")
                    .header("User-Agent", userAgentFor(uri))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return java.util.Optional.empty();
            }
            return ArticleTextCleaner.cleanFetchedHtml(response.body());
        } catch (Exception exception) {
            return java.util.Optional.empty();
        }
    }

    private boolean shouldFetchArticleText(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("merger")
                || lower.contains("acquired")
                || lower.contains("acquire")
                || lower.contains("take private")
                || lower.contains("going private")
                || lower.contains("tender offer")
                || lower.contains("shareholders will receive")
                || lower.contains("stockholders will receive");
    }

    private boolean isWhitelistedArticleHost(URI uri) {
        String host = uri.getHost();
        if (host == null || settings.articleWhitelistHosts() == null) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return settings.articleWhitelistHosts().stream()
                .map(allowedHost -> allowedHost.toLowerCase(Locale.ROOT))
                .anyMatch(allowedHost -> normalizedHost.equals(allowedHost) || normalizedHost.endsWith("." + allowedHost));
    }

    private String textOfFirst(Element element, String tagName, String fallback) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return fallback;
        }
        return nodes.item(0).getTextContent();
    }

    private Instant parsePublishedAt(String value) {
        String cleaned = cleanText(value);
        if (cleaned.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(cleaned, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String cleanText(String value) {
        return ArticleTextCleaner.cleanText(value);
    }

    private String sourceFromUrl(String feedUrl) {
        try {
            URI uri = URI.create(feedUrl);
            return uri.getHost() == null ? "RSS Feed" : uri.getHost();
        } catch (IllegalArgumentException exception) {
            return "RSS Feed";
        }
    }

    private static final java.util.regex.Pattern SEC_FORM_TITLE =
            java.util.regex.Pattern.compile(
                    "^(SC TO-T(?:/A)?|SC TO-I(?:/A)?|DEFM14A|PREM14A|DEFC14A|8-K|425|S-4)\\s+-\\s+(.+?)(?:\\s*\\(\\d+\\).*)?$",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    private String guessCompanyName(String headline) {
        String trimmed = headline.trim();
        if (trimmed.isBlank()) {
            return "Unknown Company";
        }
        // SEC EDGAR form titles: "SC TO-T - Company Name (CIK) (date)"
        Matcher secMatcher = SEC_FORM_TITLE.matcher(trimmed);
        if (secMatcher.matches()) {
            String name = secMatcher.group(2).trim();
            return name.isBlank() ? "Unknown Company" : name;
        }
        // Strip trailing " - Source" or " | Source" suffixes common in Google News headlines
        trimmed = trimmed.replaceAll("\\s+[-|]\\s+[^-|]{2,40}$", "").trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String marker : List.of(
                " announces ", " enters ", " signs ", " reports ",
                " to be acquired", " agrees to be ", " will be acquired",
                " completes ", " closes ", " reaches ")) {
            int idx = lower.indexOf(marker);
            if (idx > 2) {
                return trimmed.substring(0, idx).trim();
            }
        }
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80).trim();
    }

    private String atomLink(Element entry, String fallback) {
        NodeList links = entry.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            org.w3c.dom.Node node = links.item(i);
            if (node instanceof Element link) {
                String href = link.getAttribute("href");
                if (href != null && !href.isBlank()) return href.trim();
                String text = link.getTextContent();
                if (text != null && !text.isBlank()) return text.trim();
            }
        }
        return fallback;
    }

    private String guessTicker(String text, String companyName) {
        java.util.regex.Matcher matcher = EXCHANGE_TICKER.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).replace(".", "-").toUpperCase(Locale.ROOT);
        }
        if (companyLookupService != null
                && companyName != null
                && !companyName.isBlank()
                && !companyName.equals("Unknown Company")
                && companyName.length() >= 10) {
            try {
                return companyLookupService.findBestMatch(null, companyName)
                        .map(SecCompanyLookupService.CompanyLookupMatch::ticker)
                        .orElse("UNKNOWN");
            } catch (Exception ignored) {
                // SEC lookup unavailable — fall through to UNKNOWN
            }
        }
        return "UNKNOWN";
    }
}
