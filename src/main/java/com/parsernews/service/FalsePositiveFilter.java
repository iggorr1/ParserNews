package com.parsernews.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class FalsePositiveFilter {
    private static final List<String> DEBT_TENDER_TERMS = List.of(
            "senior notes",
            "senior secured notes",
            "unsecured notes",
            "convertible notes",
            "notes due",
            "debt securities",
            "bond",
            "bonds",
            "debentures",
            "indenture",
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
            "acquires minority stake",
            "acquires minority interest",
            "minority stake",
            "strategic investment in",
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
            "completion of public offering",
            "completion of registered direct offering",
            "completion of private placement",
            "completion of debt financing",
            "completion of asset acquisition",
            "completion of assets acquisition",
            "completion of sale of assets",
            "completion of reverse stock split",
            "completion of take-private transaction",
            "completion of going-private transaction",
            "completed acquisition",
            "completes acquisition",
            "private placement",
            "private placements",
            "public offering",
            "public offerings",
            "registered direct offering",
            "registered direct offerings",
            "credit facility",
            "financing agreement",
            "joint venture",
            "joint ventures"
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
