package com.parsernews.service;

import com.parsernews.config.SecScannerSettings;
import com.parsernews.persistence.SecWatchlistCompanyEntity;
import com.parsernews.persistence.SecWatchlistCompanyRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SecWatchlistManagerService {
    private final SecScannerSettings settings;
    private final SecWatchlistCompanyRepository repository;

    public SecWatchlistManagerService(SecScannerSettings settings, SecWatchlistCompanyRepository repository) {
        this.settings = settings;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SecWatchlistCompanyEntity> listEntries() {
        return repository.findAllByOrderByCompanyNameAscCikAsc();
    }

    @Transactional
    public SecWatchlistCompanyEntity addEntry(WatchlistRequest request) {
        String cik = normalizeCik(request.cik());
        if (repository.existsByCik(cik)) {
            throw new DuplicateSecWatchlistCompanyException("SEC watchlist CIK already exists: " + cik);
        }
        try {
            return repository.save(new SecWatchlistCompanyEntity(
                    cik,
                    requiredCompanyName(request.companyName()),
                    request.ticker(),
                    request.notes(),
                    request.enabled() == null || request.enabled()
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateSecWatchlistCompanyException("SEC watchlist CIK already exists: " + cik);
        }
    }

    @Transactional
    public SecWatchlistCompanyEntity updateEntry(Long id, WatchlistRequest request) {
        SecWatchlistCompanyEntity entry = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SEC watchlist entry not found"));
        entry.update(
                request.companyName(),
                request.ticker(),
                request.notes(),
                request.enabled()
        );
        return entry;
    }

    @Transactional
    public void deleteEntry(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("SEC watchlist entry not found");
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public ResolvedWatchlist resolveActiveWatchlist() {
        long dbWatchlistSize = repository.count();
        long dbEnabledSize = repository.countByEnabledTrue();
        List<String> envCiks = settings.watchlistCiks();
        if (dbWatchlistSize > 0) {
            return new ResolvedWatchlist(
                    WatchlistSource.DB,
                    repository.findByEnabledTrueOrderByCompanyNameAscCikAsc().stream()
                            .map(SecWatchlistCompanyEntity::getCik)
                            .toList(),
                    (int) dbWatchlistSize,
                    (int) dbEnabledSize,
                    envCiks.size()
            );
        }
        if (!envCiks.isEmpty()) {
            return new ResolvedWatchlist(
                    WatchlistSource.ENV,
                    envCiks.stream().map(SecWatchlistManagerService::normalizeCik).toList(),
                    0,
                    0,
                    envCiks.size()
            );
        }
        return new ResolvedWatchlist(WatchlistSource.NONE, List.of(), 0, 0, 0);
    }

    public static String normalizeCik(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CIK is required");
        }
        String digits = value.trim();
        if (!digits.matches("\\d{1,10}")) {
            throw new IllegalArgumentException("CIK must contain 1 to 10 digits");
        }
        String normalized = digits.replaceFirst("^0+(?!$)", "");
        return normalized.isBlank() ? "0" : normalized;
    }

    private String requiredCompanyName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
        return value.trim();
    }

    public enum WatchlistSource {
        DB,
        ENV,
        NONE
    }

    public record WatchlistRequest(
            String cik,
            String companyName,
            String ticker,
            String notes,
            Boolean enabled
    ) {
    }

    public record ResolvedWatchlist(
            WatchlistSource source,
            List<String> ciks,
            int dbWatchlistSize,
            int dbEnabledSize,
            int envWatchlistSize
    ) {
        public int activeWatchlistSize() {
            return ciks.size();
        }
    }

    public static class DuplicateSecWatchlistCompanyException extends RuntimeException {
        public DuplicateSecWatchlistCompanyException(String message) {
            super(message);
        }
    }
}
