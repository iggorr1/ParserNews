package com.parsernews.parser;

import com.parsernews.config.RssSettings;
import com.parsernews.model.NewsEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RssNewsParserTest {
    @Test
    void parsesRssItemsIntoNewsEvents() {
        RssNewsParser parser = new RssNewsParser(
                new RssSettings(List.of("https://example.com/feed.xml"), 5, 10, false, List.of()),
                new StubHttpClient("""
                        <?xml version="1.0" encoding="utf-8"?>
                        <rss version="2.0">
                          <channel>
                            <title>Test Feed</title>
                            <item>
                              <title>Example Corp Enters Definitive Merger Agreement</title>
                              <link>https://example.com/news/1</link>
                              <description>Shareholders will receive $5.00 per share in cash.</description>
                            </item>
                          </channel>
                        </rss>
                        """)
        );

        List<NewsEvent> events = parser.readNews();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().source()).isEqualTo("Test Feed");
        assertThat(events.getFirst().headline()).isEqualTo("Example Corp Enters Definitive Merger Agreement");
        assertThat(events.getFirst().ticker()).isEqualTo("UNKNOWN");
        assertThat(events.getFirst().body()).contains("per share in cash");
        assertThat(events.getFirst().sourceUrl()).isEqualTo("https://example.com/news/1");
    }

    @Test
    void extractsTickerFromExchangeLabel() {
        RssNewsParser parser = new RssNewsParser(
                new RssSettings(List.of("https://example.com/feed.xml"), 5, 10, false, List.of()),
                new StubHttpClient("""
                        <?xml version="1.0" encoding="utf-8"?>
                        <rss version="2.0">
                          <channel>
                            <title>Test Feed</title>
                            <item>
                              <title>Open Lending Enters Into Merger Agreement</title>
                              <link>https://example.com/news/open</link>
                              <description>Open Lending Corporation (NYSE American: OPEN) will be acquired for cash.</description>
                            </item>
                          </channel>
                        </rss>
                        """)
        );

        List<NewsEvent> events = parser.readNews();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().ticker()).isEqualTo("OPEN");
    }

    @Test
    void doesNotTreatExchangeNameAsTickerWithoutColon() {
        RssNewsParser parser = new RssNewsParser(
                new RssSettings(List.of("https://example.com/feed.xml"), 5, 10, false, List.of()),
                new StubHttpClient("""
                        <?xml version="1.0" encoding="utf-8"?>
                        <rss version="2.0">
                          <channel>
                            <title>Test Feed</title>
                            <item>
                              <title>Company Receives Notice from Nasdaq Regarding Listing Rule</title>
                              <link>https://example.com/news/listing</link>
                              <description>No ticker label is present in this article.</description>
                            </item>
                          </channel>
                        </rss>
                        """)
        );

        List<NewsEvent> events = parser.readNews();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().ticker()).isEqualTo("UNKNOWN");
    }

    @Test
    void rejectsNonHttpsFeeds() {
        RssNewsParser parser = new RssNewsParser(
                new RssSettings(List.of("http://example.com/feed.xml"), 5, 10, false, List.of()),
                new StubHttpClient("<rss />")
        );

        assertThatThrownBy(parser::readNews)
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Only HTTPS RSS feeds are allowed: http://example.com/feed.xml");
    }

    @Test
    void continuesWhenOneFeedFails() {
        RssNewsParser parser = new RssNewsParser(
                new RssSettings(List.of(
                        "https://example.com/broken.xml",
                        "https://example.com/healthy.xml"
                ), 5, 10, false, List.of()),
                new UriAwareStubHttpClient(
                        Map.of("https://example.com/healthy.xml", """
                                <?xml version="1.0" encoding="utf-8"?>
                                <rss version="2.0">
                                  <channel>
                                    <title>Healthy Feed</title>
                                    <item>
                                      <title>Target Corp to be acquired by Sponsor LLC</title>
                                      <link>https://example.com/news/2</link>
                                      <description>All-cash transaction.</description>
                                    </item>
                                  </channel>
                                </rss>
                                """),
                        Set.of("https://example.com/broken.xml")
                )
        );

        List<NewsEvent> events = parser.readNews();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().source()).isEqualTo("Healthy Feed");
        assertThat(events.getFirst().headline()).contains("acquired");
    }

    @Test
    void fetchesFullArticleTextForWhitelistedHosts() {
        RssNewsParser parser = new RssNewsParser(
                new RssSettings(List.of("https://example.com/feed.xml"), 5, 10, true, List.of("example.com")),
                new UriAwareStubHttpClient(
                        Map.of(
                                "https://example.com/feed.xml", """
                                        <?xml version="1.0" encoding="utf-8"?>
                                        <rss version="2.0">
                                          <channel>
                                            <title>Test Feed</title>
                                            <item>
                                              <title>Target Corp to be Acquired by Buyer LLC</title>
                                              <link>https://example.com/news/full</link>
                                              <description>Short RSS description.</description>
                                            </item>
                                          </channel>
                                        </rss>
                                        """,
                                "https://example.com/news/full", """
                                        <html><body>Target Corp (NASDAQ: TGTX) shareholders will receive $3.15 per share in cash.</body></html>
                                        """
                        ),
                        Set.of()
                )
        );

        List<NewsEvent> events = parser.readNews();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().ticker()).isEqualTo("TGTX");
        assertThat(events.getFirst().body()).contains("$3.15 per share in cash");
    }

    private static class StubHttpClient extends HttpClient {
        private final String body;

        private StubHttpClient(String body) {
            this.body = body;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            return new StubHttpResponse<>((T) body, request.uri());
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("Not needed in tests");
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException("Not needed in tests");
        }

        @Override
        public Optional<java.net.CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NORMAL;
        }

        @Override
        public Optional<java.net.ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<java.net.Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_2;
        }

        @Override
        public Optional<java.util.concurrent.Executor> executor() {
            return Optional.empty();
        }
    }

    private record StubHttpResponse<T>(T body, URI uri) implements HttpResponse<T> {
        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(uri).build();
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return uri;
        }

        @Override
        public Version version() {
            return Version.HTTP_2;
        }
    }

    private static class UriAwareStubHttpClient extends StubHttpClient {
        private final Map<String, String> responses;
        private final Set<String> brokenUrls;

        private UriAwareStubHttpClient(Map<String, String> responses, Set<String> brokenUrls) {
            super("<rss />");
            this.responses = responses;
            this.brokenUrls = brokenUrls;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            String url = request.uri().toString();
            if (brokenUrls.contains(url)) {
                throw new IOException("Feed unavailable");
            }
            return new StubHttpResponse<>((T) responses.getOrDefault(url, "<rss />"), request.uri());
        }
    }
}
