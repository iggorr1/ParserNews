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
    private static final List<String> NON_BUYOUT_TERMS = List.of(
            "asset acquisition",
            "assets acquisition",
            "acquisition of assets",
            "acquires assets",
            "acquires the assets",
            "brand acquisition",
            "acquires brand",
            "franchise acquisition",
            "franchisor business",
            "property acquisition",
            "real estate acquisition",
            "sale of facility",
            "sale of assets",
            "sale of substantially all assets",
            "strategic exit",
            "reverse merger",
            "announces completion",
            "completion of",
            "completed acquisition",
            "completes acquisition",
            "private placement",
            "public offering",
            "registered direct offering",
            "credit facility",
            "financing agreement",
            "joint venture"
    );

    public boolean isDebtTenderOffer(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.contains("tender offer") && !lower.contains("tender offers")) {
            return false;
        }
        return DEBT_TENDER_TERMS.stream().anyMatch(lower::contains);
    }

    public List<String> reasons(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> reasons = new java.util.ArrayList<>();
        if (isDebtTenderOffer(text)) {
            reasons.addAll(DEBT_TENDER_TERMS.stream()
                    .filter(lower::contains)
                    .toList());
        }
        reasons.addAll(NON_BUYOUT_TERMS.stream()
                .filter(lower::contains)
                .toList());
        return reasons;
    }

    public boolean isNonBuyoutAcquisition(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return NON_BUYOUT_TERMS.stream().anyMatch(lower::contains);
    }
}
