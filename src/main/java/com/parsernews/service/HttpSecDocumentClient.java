package com.parsernews.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpSecDocumentClient implements SecDocumentClient {
    private static final String USER_AGENT = "ParserNews SEC Watchlist Scanner/1.0 contact: local-research@example.invalid";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public String fetchDocumentText(String documentUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(documentUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "text/html, text/plain, */*")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("SEC document request failed: HTTP " + response.statusCode());
        }
        return response.body();
    }
}
