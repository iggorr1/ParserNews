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
import java.time.LocalDate;

@Component
public class HttpSecFullTextSearchClient implements SecFullTextSearchClient {
    private static final String BASE = "https://efts.sec.gov/LATEST/search-index";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public String search(String query, String forms, LocalDate startDate, LocalDate endDate)
            throws IOException, InterruptedException {
        // Quote the phrase for an exact-phrase EFTS match.
        String q = URLEncoder.encode("\"" + query + "\"", StandardCharsets.UTF_8);
        StringBuilder url = new StringBuilder(BASE).append("?q=").append(q);
        if (forms != null && !forms.isBlank()) {
            url.append("&forms=").append(URLEncoder.encode(forms, StandardCharsets.UTF_8));
        }
        if (startDate != null && endDate != null) {
            url.append("&startdt=").append(startDate).append("&enddt=").append(endDate);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", SecHttpUserAgent.VALUE)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("EFTS request failed for query \"" + query + "\": HTTP " + response.statusCode());
        }
        return response.body();
    }
}
