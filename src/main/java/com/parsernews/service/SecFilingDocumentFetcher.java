package com.parsernews.service;

import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SecFilingDocumentFetcher {
    private static final int SNIPPET_MAX_LENGTH = 4000;
    private static final int FAILURE_REASON_MAX_LENGTH = 1024;
    private static final Pattern NUMERIC_ENTITY_PATTERN = Pattern.compile("&#(x?[0-9a-fA-F]+);");

    private final SecFilingRepository filingRepository;
    private final SecDocumentClient documentClient;

    public SecFilingDocumentFetcher(SecFilingRepository filingRepository, SecDocumentClient documentClient) {
        this.filingRepository = filingRepository;
        this.documentClient = documentClient;
    }

    @Transactional
    public SecDocumentFetchSummary fetchPendingDocuments() {
        int attemptedCount = 0;
        int fetchedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        List<SecFilingEntity> filings = filingRepository.findTop50ByDocumentFetchedAtIsNullOrderByFilingDateDescProcessedAtDesc();

        for (SecFilingEntity filing : filings) {
            attemptedCount++;
            if (filing.getDocumentFetchedAt() != null) {
                skippedCount++;
                continue;
            }
            if (fetchDocumentForEntity(filing)) {
                fetchedCount++;
            } else {
                failedCount++;
            }
            sleepConservatively();
        }

        return new SecDocumentFetchSummary(attemptedCount, fetchedCount, skippedCount, failedCount);
    }

    @Transactional
    public SecFilingEntity fetchDocument(Long filingId) {
        SecFilingEntity filing = filingRepository.findById(filingId)
                .orElseThrow(() -> new EntityNotFoundException("SEC filing not found: " + filingId));
        if (filing.getDocumentFetchedAt() != null) {
            return filing;
        }
        fetchDocumentForEntity(filing);
        return filing;
    }

    private boolean fetchDocumentForEntity(SecFilingEntity filing) {
        String documentUrl = buildDocumentUrl(filing.getCik(), filing.getAccessionNumber(), filing.getPrimaryDocument());
        try {
            String rawText = documentClient.fetchDocumentText(documentUrl);
            String cleanedText = cleanText(rawText);
            String snippet = truncate(cleanedText, SNIPPET_MAX_LENGTH);
            SecDocumentSignal signal = analyze(cleanedText);
            filing.markDocumentFetched(documentUrl, snippet, signal.strength(), signal.reason());
            return true;
        } catch (IOException | RuntimeException exception) {
            filing.markDocumentFetchFailed(documentUrl, truncate("Document fetch failed: " + exception.getMessage(), FAILURE_REASON_MAX_LENGTH));
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            filing.markDocumentFetchFailed(documentUrl, "Document fetch interrupted.");
            return false;
        }
    }

    static String buildDocumentUrl(String cik, String accessionNumber, String primaryDocument) {
        String paddedCik = SecWatchlistScanner.padCik(cik);
        String cikNoLeadingZeros = Long.toString(Long.parseLong(paddedCik));
        String accessionNoDashes = accessionNumber == null ? "" : accessionNumber.replace("-", "");
        String document = primaryDocument == null ? "" : primaryDocument.trim();
        return "https://www.sec.gov/Archives/edgar/data/"
                + cikNoLeadingZeros + "/" + accessionNoDashes + "/" + document;
    }

    static String cleanText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String text = rawText
                .replaceAll("(?is)<script\\b[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style\\b[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<noscript\\b[^>]*>.*?</noscript>", " ")
                .replaceAll("(?is)<!--.*?-->", " ")
                .replaceAll("(?is)<[^>]+>", " ");
        text = text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        text = decodeNumericEntities(text);
        text = text.replace('\u00A0', ' ');
        return text.replaceAll("\\s+", " ").trim();
    }

    static SecDocumentSignal analyze(String text) {
        String normalized = normalize(text);
        if (containsAny(normalized, "agreement and plan of merger", "merger agreement")) {
            return new SecDocumentSignal("HIGH", "Document text mentions merger agreement language.");
        }
        if (containsAny(normalized, "tender offer", "offer to purchase")) {
            return new SecDocumentSignal("HIGH", "Document text mentions tender offer / offer to purchase language.");
        }
        if (containsAny(normalized, "going private", "go private")) {
            return new SecDocumentSignal("HIGH", "Document text mentions going-private language.");
        }
        if (containsAny(normalized, "acquisition agreement")) {
            return new SecDocumentSignal("HIGH", "Document text mentions acquisition agreement language.");
        }
        if (containsAny(normalized, "sale of substantially all assets")) {
            return new SecDocumentSignal("HIGH", "Document text mentions sale of substantially all assets.");
        }
        if (containsAny(normalized, "definitive proxy statement", "business combination", "change in control")) {
            return new SecDocumentSignal("MEDIUM", "Document text mentions proxy, business combination, or change-in-control language.");
        }
        return new SecDocumentSignal("NONE", "No strong M&A document signal found.");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9$%.]+", " ");
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static String decodeNumericEntities(String text) {
        Matcher matcher = NUMERIC_ENTITY_PATTERN.matcher(text);
        StringBuilder decoded = new StringBuilder();
        while (matcher.find()) {
            String value = matcher.group(1);
            try {
                int radix = value.startsWith("x") || value.startsWith("X") ? 16 : 10;
                String digits = radix == 16 ? value.substring(1) : value;
                matcher.appendReplacement(decoded, Matcher.quoteReplacement(Character.toString(Integer.parseInt(digits, radix))));
            } catch (IllegalArgumentException exception) {
                matcher.appendReplacement(decoded, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(decoded);
        return decoded.toString();
    }

    private void sleepConservatively() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public record SecDocumentFetchSummary(
            int attemptedCount,
            int fetchedCount,
            int skippedCount,
            int failedCount
    ) {
    }

    record SecDocumentSignal(String strength, String reason) {
    }
}
