package com.parsernews.web;

import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.persistence.SecSignalPriority;
import com.parsernews.persistence.SecSignalType;
import com.parsernews.persistence.SecWatchlistCompanyEntity;
import com.parsernews.service.SecFilingDocumentFetcher;
import com.parsernews.service.SecWatchlistManagerService;
import com.parsernews.service.SecWatchlistScanner;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@RestController
public class SecController {
    private final SecWatchlistScanner secWatchlistScanner;
    private final SecFilingRepository filingRepository;
    private final SecFilingDocumentFetcher documentFetcher;
    private final SecWatchlistManagerService watchlistManagerService;

    public SecController(
            SecWatchlistScanner secWatchlistScanner,
            SecFilingRepository filingRepository,
            SecFilingDocumentFetcher documentFetcher,
            SecWatchlistManagerService watchlistManagerService
    ) {
        this.secWatchlistScanner = secWatchlistScanner;
        this.filingRepository = filingRepository;
        this.documentFetcher = documentFetcher;
        this.watchlistManagerService = watchlistManagerService;
    }

    @PostMapping("/api/sec/scan")
    public SecWatchlistScanner.SecScanSummary scan() {
        return secWatchlistScanner.scan();
    }

    @GetMapping("/api/sec/status")
    public SecWatchlistScanner.SecStatus status() {
        return secWatchlistScanner.status();
    }

    @GetMapping("/api/sec/watchlist")
    @Transactional(readOnly = true)
    public List<SecWatchlistCompanyResponse> watchlist() {
        return watchlistManagerService.listEntries().stream()
                .map(SecWatchlistCompanyResponse::from)
                .toList();
    }

    @PostMapping("/api/sec/watchlist")
    @Transactional
    public SecWatchlistCompanyResponse addWatchlistEntry(@RequestBody SecWatchlistManagerService.WatchlistRequest request) {
        try {
            return SecWatchlistCompanyResponse.from(watchlistManagerService.addEntry(request));
        } catch (IllegalArgumentException | SecWatchlistManagerService.DuplicateSecWatchlistCompanyException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PatchMapping("/api/sec/watchlist/{id}")
    @Transactional
    public SecWatchlistCompanyResponse updateWatchlistEntry(
            @PathVariable Long id,
            @RequestBody SecWatchlistManagerService.WatchlistRequest request
    ) {
        try {
            return SecWatchlistCompanyResponse.from(watchlistManagerService.updateEntry(id, request));
        } catch (EntityNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SEC watchlist entry not found", exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/api/sec/watchlist/{id}")
    @Transactional
    public ResponseEntity<Void> deleteWatchlistEntry(@PathVariable Long id) {
        try {
            watchlistManagerService.deleteEntry(id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SEC watchlist entry not found", exception);
        }
    }

    @GetMapping("/api/sec/filings")
    @Transactional(readOnly = true)
    public List<SecFilingResponse> filings() {
        return filingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc().stream()
                .map(SecFilingResponse::from)
                .toList();
    }

    @GetMapping("/api/sec/filings/reviewed")
    @Transactional(readOnly = true)
    public List<SecFilingResponse> reviewedFilings(
            @RequestParam(required = false) ManualReviewStatus status,
            @RequestParam(required = false) ManualReviewReason reason,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return filingRepository.findTop200ByManualReviewStatusInOrderByManualReviewedAtDesc(List.of(ManualReviewStatus.USEFUL, ManualReviewStatus.IGNORED)).stream()
                .filter(filing -> status == null || filing.getManualReviewStatus() == status)
                .filter(filing -> reason == null || filing.getManualReviewReason() == reason)
                .limit(normalizedLimit(limit))
                .map(SecFilingResponse::from)
                .toList();
    }

    @GetMapping(value = "/api/sec/filings/export.csv", produces = "text/csv")
    @Transactional(readOnly = true)
    public ResponseEntity<String> exportFilingsCsv(
            @RequestParam(required = false) SecSignalPriority priority,
            @RequestParam(required = false) SecSignalType type,
            @RequestParam(required = false) ManualReviewStatus status,
            @RequestParam(defaultValue = "500") int limit
    ) {
        List<SecFilingResponse> rows = filingRepository.findAll().stream()
                .filter(filing -> priority == null || filing.getSecSignalPriority() == priority)
                .filter(filing -> type == null || filing.getSecSignalType() == type)
                .filter(filing -> status == null || filing.getManualReviewStatus() == status)
                .sorted(Comparator.comparing(SecFilingEntity::getFilingDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SecFilingEntity::getProcessedAt, Comparator.reverseOrder()))
                .limit(normalizedExportLimit(limit))
                .map(SecFilingResponse::from)
                .toList();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"parsernews-sec-filings.csv\"")
                .body(toCsv(rows));
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

    @PatchMapping("/api/sec/filings/{id}/manual-review")
    @Transactional
    public SecFilingResponse updateManualReview(
            @PathVariable Long id,
            @RequestBody SecManualReviewRequest request
    ) {
        SecFilingEntity filing = filingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SEC filing not found"));
        filing.updateManualReview(request.status(), request.reason(), request.note());
        return SecFilingResponse.from(filing);
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
            String documentSignalReason,
            SecSignalType secSignalType,
            SecSignalPriority secSignalPriority,
            String secSignalSummary,
            String secSignalWarnings,
            ManualReviewStatus manualReviewStatus,
            ManualReviewReason manualReviewReason,
            String manualReviewNote,
            Instant manualReviewedAt
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
                    filing.getDocumentSignalReason(),
                    filing.getSecSignalType(),
                    filing.getSecSignalPriority(),
                    filing.getSecSignalSummary(),
                    filing.getSecSignalWarnings(),
                    filing.getManualReviewStatus(),
                    filing.getManualReviewReason(),
                    filing.getManualReviewNote(),
                    filing.getManualReviewedAt()
            );
        }
    }

    public record SecManualReviewRequest(
            ManualReviewStatus status,
            ManualReviewReason reason,
            String note
    ) {
    }

    public record SecWatchlistCompanyResponse(
            Long id,
            String cik,
            String companyName,
            String ticker,
            String notes,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
        static SecWatchlistCompanyResponse from(SecWatchlistCompanyEntity entry) {
            return new SecWatchlistCompanyResponse(
                    entry.getId(),
                    entry.getCik(),
                    entry.getCompanyName(),
                    entry.getTicker(),
                    entry.getNotes(),
                    entry.isEnabled(),
                    entry.getCreatedAt(),
                    entry.getUpdatedAt()
            );
        }
    }

    private int normalizedLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 200);
    }

    private int normalizedExportLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 500);
    }

    private String toCsv(List<SecFilingResponse> rows) {
        List<String> headers = List.of(
                "id",
                "companyName",
                "cik",
                "form",
                "filingDate",
                "accessionNumber",
                "filingUrl",
                "documentUrl",
                "secSignalType",
                "secSignalPriority",
                "secSignalSummary",
                "secSignalWarnings",
                "documentSignalReason",
                "manualReviewStatus",
                "manualReviewReason",
                "manualReviewNote",
                "manualReviewedAt"
        );
        StringBuilder csv = new StringBuilder(String.join(",", headers)).append('\n');
        for (SecFilingResponse row : rows) {
            csv.append(csvRow(Arrays.asList(
                    row.id(),
                    row.companyName(),
                    row.cik(),
                    row.form(),
                    row.filingDate(),
                    row.accessionNumber(),
                    row.filingUrl(),
                    row.documentUrl(),
                    row.secSignalType(),
                    row.secSignalPriority(),
                    row.secSignalSummary(),
                    row.secSignalWarnings(),
                    row.documentSignalReason(),
                    row.manualReviewStatus(),
                    row.manualReviewReason(),
                    row.manualReviewNote(),
                    row.manualReviewedAt()
            ))).append('\n');
        }
        return csv.toString();
    }

    private String csvRow(List<?> values) {
        return values.stream()
                .map(this::csvCell)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
