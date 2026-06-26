package com.parsernews.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class HttpSecCurrentFilingsClient implements SecCurrentFilingsClient {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public String fetchCurrentFilingsAtom(String form, int count) throws IOException, InterruptedException {
        String encodedForm = URLEncoder.encode(form, StandardCharsets.UTF_8);
        int normalizedCount = Math.max(1, Math.min(count, 100));
        URI uri = URI.create("https://www.sec.gov/cgi-bin/browse-edgar?action=getcurrent&type="
                + encodedForm
                + "&owner=include&count="
                + normalizedCount
                + "&output=atom");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/atom+xml, application/xml, text/xml, */*")
                .header("User-Agent", SecHttpUserAgent.VALUE)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("SEC current filings request failed for form " + form + ": HTTP " + response.statusCode());
        }
        return response.body();
    }
}
