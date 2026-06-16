package com.parsernews.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class FalsePositiveFilter {
    private static final List<String> DEBT_TENDER_TERMS = List.of(
            "senior notes",
            "notes due",
            "debt securities",
            "bond",
            "bonds",
            "debentures",
            "credit facility",
            "term loan",
            "exchange offer",
            "debt tender offer"
    );

    public boolean isDebtTenderOffer(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.contains("tender offer") && !lower.contains("tender offers")) {
            return false;
        }
        return DEBT_TENDER_TERMS.stream().anyMatch(lower::contains);
    }

    public List<String> reasons(String text) {
        if (!isDebtTenderOffer(text)) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return DEBT_TENDER_TERMS.stream()
                .filter(lower::contains)
                .toList();
    }
}
