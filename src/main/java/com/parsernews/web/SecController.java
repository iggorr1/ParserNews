package com.parsernews.web;

import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.service.SecWatchlistScanner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
public class SecController {
    private final SecWatchlistScanner secWatchlistScanner;
    private final SecFilingRepository filingRepository;

    public SecController(SecWatchlistScanner secWatchlistScanner, SecFilingRepository filingRepository) {
        this.secWatchlistScanner = secWatchlistScanner;
        this.filingRepository = filingRepository;
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
            Instant processedAt
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
                    filing.getProcessedAt()
            );
        }
    }
}
