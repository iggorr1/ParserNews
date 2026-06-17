package com.parsernews.util;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ArticleTextCleaner {
    private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?is)<script\\b[^>]*>.*?</script>");
    private static final Pattern STYLE_BLOCK = Pattern.compile("(?is)<style\\b[^>]*>.*?</style>");
    private static final Pattern NOSCRIPT_BLOCK = Pattern.compile("(?is)<noscript\\b[^>]*>.*?</noscript>");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final List<Pattern> ARTICLE_BODY_PATTERNS = List.of(
            Pattern.compile("(?is)<article\\b[^>]*>(.*?)</article>"),
            Pattern.compile("(?is)<main\\b[^>]*>(.*?)</main>"),
            Pattern.compile("(?is)<div\\b[^>]*class\\s*=\\s*[\"'][^\"']*(?:article[-_\\s]?body|article[-_\\s]?content|release[-_\\s]?body|news[-_\\s]?release[-_\\s]?body|main[-_\\s]?body)[^\"']*[\"'][^>]*>(.*?)</div>")
    );
    private static final List<String> BOILERPLATE_MARKERS = List.of(
            "googletagmanager",
            "datalayer",
            "function (w, d, s, l, i)",
            "function(w,d,s,l,i)",
            "box-sizing"
    );

    private ArticleTextCleaner() {
    }

    public static Optional<String> cleanFetchedHtml(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        String withoutBlocks = removeCodeBlocks(html);
        for (Pattern pattern : ARTICLE_BODY_PATTERNS) {
            java.util.regex.Matcher matcher = pattern.matcher(withoutBlocks);
            while (matcher.find()) {
                String candidate = cleanText(matcher.group(1));
                if (isReadable(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        String fallback = cleanText(withoutBlocks);
        return isReadable(fallback) ? Optional.of(fallback) : Optional.empty();
    }

    public static String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String withoutBlocks = removeCodeBlocks(value);
        String withoutTags = HTML_TAG.matcher(withoutBlocks).replaceAll(" ");
        String decoded = withoutTags
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return WHITESPACE.matcher(decoded).replaceAll(" ").trim();
    }

    public static String cleanTextForSnippet(String value, String fallback) {
        String cleaned = cleanText(value);
        if (isReadable(cleaned)) {
            return cleaned;
        }
        return cleanText(fallback);
    }

    private static String removeCodeBlocks(String value) {
        String withoutScripts = SCRIPT_BLOCK.matcher(value).replaceAll(" ");
        String withoutStyles = STYLE_BLOCK.matcher(withoutScripts).replaceAll(" ");
        return NOSCRIPT_BLOCK.matcher(withoutStyles).replaceAll(" ");
    }

    private static boolean isReadable(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase();
        for (String marker : BOILERPLATE_MARKERS) {
            if (lower.contains(marker)) {
                return false;
            }
        }
        return true;
    }
}
