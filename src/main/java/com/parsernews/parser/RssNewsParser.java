package com.parsernews.parser;

import com.parsernews.config.RssSettings;
import com.parsernews.model.NewsEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "scanner.source", havingValue = "rss")
public class RssNewsParser implements NewsSourceParser {
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EXCHANGE_TICKER = Pattern.compile(
            "(?i)(?:NYSE\\s+American|NYSEAMERICAN|NASDAQ|NYSE|AMEX|OTCQB|OTCQX|OTC|OTCMKTS|TSX|TSXV)\\s*:\\s*([A-Z][A-Z0-9.-]{0,9})"
    );

    private final RssSettings settings;
    private final HttpClient httpClient;

    @Autowired
    public RssNewsParser(RssSettings settings) {
        this(settings, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    RssNewsParser(RssSettings settings, HttpClient httpClient) {
        this.settings = settings;
        this.httpClient = httpClient;
    }

    @Override
    public List<NewsEvent> readNews() {
        List<NewsEvent> events = new ArrayList<>();
        int failedFeeds = 0;

        for (String url : settings.urls()) {
            try {
                events.addAll(readFeed(url));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid RSS feed configuration: " + url, exception);
            } catch (IllegalStateException exception) {
                failedFeeds++;
                System.err.println("Warning: skipped RSS feed because it could not be read: " + url);
                System.err.println("Reason: " + exception.getMessage());
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
                    .header("User-Agent", "ParserNews/1.0 research-news-scanner")
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
        NodeList items = document.getElementsByTagName("item");
        List<NewsEvent> events = new ArrayList<>();
        int limit = Math.min(items.getLength(), settings.maxItemsPerFeed());

        for (int index = 0; index < limit; index++) {
            Element item = (Element) items.item(index);
            String headline = cleanText(textOfFirst(item, "title", ""));
            String body = cleanText(textOfFirst(item, "description", ""));
            String link = cleanText(textOfFirst(item, "link", feedUrl));
            String articleBody = fetchFullArticleText(link, headline + " " + body).orElse(body);
            Instant publishedAt = parsePublishedAt(textOfFirst(item, "pubDate", ""));
            String companyName = guessCompanyName(headline);
            String ticker = guessTicker(headline + " " + articleBody);

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
                    .header("User-Agent", "ParserNews/1.0 research-news-scanner")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(cleanText(response.body()));
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
        String withoutTags = HTML_TAG.matcher(value).replaceAll(" ");
        String decoded = withoutTags
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return WHITESPACE.matcher(decoded).replaceAll(" ").trim();
    }

    private String sourceFromUrl(String feedUrl) {
        try {
            URI uri = URI.create(feedUrl);
            return uri.getHost() == null ? "RSS Feed" : uri.getHost();
        } catch (IllegalArgumentException exception) {
            return "RSS Feed";
        }
    }

    private String guessCompanyName(String headline) {
        String trimmed = headline.trim();
        if (trimmed.isBlank()) {
            return "Unknown Company";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int announcesIndex = lower.indexOf(" announces ");
        if (announcesIndex > 0) {
            return trimmed.substring(0, announcesIndex).trim();
        }
        int entersIndex = lower.indexOf(" enters ");
        if (entersIndex > 0) {
            return trimmed.substring(0, entersIndex).trim();
        }
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80).trim();
    }

    private String guessTicker(String text) {
        java.util.regex.Matcher matcher = EXCHANGE_TICKER.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).replace(".", "-").toUpperCase(Locale.ROOT);
        }
        return "UNKNOWN";
    }
}
