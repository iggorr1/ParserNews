package com.parsernews.service;

import java.util.Locale;
import java.util.regex.Pattern;

final class NewsTextPatterns {
    static final String ROUNDUP_AGGREGATOR_WARNING = "roundup/aggregator article, not primary source";
    private static final Pattern PER_SHARE_PRICE = Pattern.compile(
            "(?i)(?:\\$|€|£)\\s*\\d+(?:\\.\\d+)?\\s*(?:per share|a share)"
    );

    private NewsTextPatterns() {
    }

    static boolean isRoundupAggregator(String... values) {
        String text = String.join(" ", values == null ? new String[0] : values)
                .toLowerCase(Locale.ROOT);
        return text.contains("press releases you need to see this week")
                || text.contains("top press releases")
                || text.contains("news roundup")
                || text.contains("weekly roundup")
                || text.contains("weekly recap")
                || text.contains("in case you missed it");
    }

    static boolean hasStrongMaPhrase(String... values) {
        String text = normalize(values);
        return text.contains("definitive agreement to acquire")
                || text.contains("agrees to acquire")
                || text.contains("to be acquired by")
                || text.contains("merger agreement")
                || text.contains("cash tender offer")
                || text.contains("all-cash transaction")
                || text.contains("cash consideration")
                || text.contains("per share in cash")
                || text.contains("fixed exchange ratio")
                || text.contains("stock-for-stock merger");
    }

    static boolean hasDealHeadlineCue(String value) {
        String text = normalize(value);
        return text.contains("acquire")
                || text.contains("acquisition")
                || text.contains("to be acquired")
                || text.contains("merger")
                || text.contains("tender offer")
                || text.contains("takeover")
                || text.contains("take private")
                || text.contains("going private")
                || text.contains("cash consideration")
                || text.contains("per share in cash")
                || text.contains("definitive agreement")
                || text.contains("offer to purchase")
                || text.contains("proposal to acquire")
                || text.contains("stock-for-stock");
    }

    static boolean hasCashOrFixedDealTerms(String... values) {
        String text = normalize(values);
        return text.contains("cash")
                || text.contains("all-cash")
                || text.contains("per share in cash")
                || text.contains("cash consideration")
                || text.contains("tender offer")
                || text.contains("fixed price")
                || PER_SHARE_PRICE.matcher(text).find();
    }

    private static String normalize(String... values) {
        return String.join(" ", values == null ? new String[0] : values)
                .toLowerCase(Locale.ROOT);
    }
}
