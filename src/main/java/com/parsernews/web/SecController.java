package com.parsernews.web;

import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.service.SecFilingDocumentFetcher;
import com.parsernews.service.SecWatchlistScanner;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
public class SecController {
    private final SecWatchlistScanner secWatchlistScanner;
    private final SecFilingRepository filingRepository;
    private final SecFilingDocumentFetcher documentFetcher;

    public SecController(
            SecWatchlistScanner secWatchlistScanner,
            SecFilingRepository filingRepository,
            SecFilingDocumentFetcher documentFetcher
    ) {
        this.secWatchlistScanner = secWatchlistScanner;
        this.filingRepository = filingRepository;
        this.documentFetcher = documentFetcher;
    }

    @PostMapping("/api/sec/scan")
    public SecWatchlistScanner.SecScanSummary scan() {
        return secWatchlistScanner.scan();
    }

    @GetMapping("/api/sec/status")
    public SecWatchlistScanner.SecStatus status() {
        return secWatchlistScanner.status();
    }

    @GetMapping("/api/sec/filings")
    @Transactional(readOnly = true)
    public List<SecFilingResponse> filings() {
        return filingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc().stream()
                .map(SecFilingResponse::from)
                .toList();
    }

    @GetMapping("/api/sec/filings/{id}")
    @Transactional(readOnly = true)
    public SecFilingResponse filing(@PathVariable Long id) {
        return filingRepository.findById(id)
                .map(SecFilingResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SEC filing not found"));
    }

    @PostMapping("/api/sec/filings/{id}/fetch-document")
    @Transactional
    public SecFilingResponse fetchDocument(@PathVariable Long id) {
        try {
            return SecFilingResponse.from(documentFetcher.fetchDocument(id));
        } catch (EntityNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SEC filing not found", exception);
        }
    }

    @PostMapping("/api/sec/fetch-documents")
    public SecFilingDocumentFetcher.SecDocumentFetchSummary fetchDocuments() {
        return documentFetcher.fetchPendingDocuments();
    }

    public record SecFilingResponse(
            Long id,
            String cik,
            String companyName,
            String form,
            LocalDate filingDate,
            String accessionNumber,
            String primaryDocument,
            String filingUrl,
            String signalType,
            String signalReason,
            Instant processedAt,
            String documentUrl,
            String documentTextSnippet,
            Instant documentFetchedAt,
            String documentFetchStatus,
            String documentSignalStrength,
            String documentSignalReason
    ) {
        static SecFilingResponse from(SecFilingEntity filing) {
            return new SecFilingResponse(
                    filing.getId(),
                    filing.getCik(),
                    filing.getCompanyName(),
                    filing.getForm(),
                    filing.getFilingDate(),
                    filing.getAccessionNumber(),
                    filing.getPrimaryDocument(),
                    filing.getFilingUrl(),
                    filing.getSignalType(),
                    filing.getSignalReason(),
                    filing.getProcessedAt(),
                    filing.getDocumentUrl(),
                    filing.getDocumentTextSnippet(),
                    filing.getDocumentFetchedAt(),
                    filing.getDocumentFetchStatus(),
                    filing.getDocumentSignalStrength(),
                    filing.getDocumentSignalReason()
            );
        }
    }
}
