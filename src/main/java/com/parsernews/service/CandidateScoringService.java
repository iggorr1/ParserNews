package com.parsernews.service;

import com.parsernews.persistence.CandidateStrength;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CandidateScoringService {
    private static final List<String> HIGH_SIGNALS = List.of(
            "definitive agreement",
            "merger agreement",
            "to be acquired by",
            "acquired by",
            "take private",
            "all-cash transaction",
            "enterprise value"
    );
    private static final List<String> MEDIUM_SIGNALS = List.of(
            "tender offer",
            "proposal to acquire",
            "strategic alternatives",
            "go-private proposal"
    );
    private static final List<String> LOW_SIGNALS = List.of(
            "rumor",
            "exploring sale",
            "considering sale",
            "interest from"
    );

    public CandidateScore score(String headline, String body) {
        String text = ((headline == null ? "" : headline) + " " + (body == null ? "" : body))
                .toLowerCase(Locale.ROOT);
        if (NewsTextPatterns.isRoundupAggregator(headline, body)) {
            return new CandidateScore(0, CandidateStrength.NONE, "Roundup/aggregator article, not primary source.");
        }
        List<String> matchedHigh   = dedup(HIGH_SIGNALS.stream().filter(text::contains).toList());
        List<String> matchedMedium = dedup(MEDIUM_SIGNALS.stream().filter(text::contains).toList());
        List<String> matchedLow    = dedup(LOW_SIGNALS.stream().filter(text::contains).toList());

        if (matchedHigh.isEmpty() && matchedMedium.isEmpty() && matchedLow.isEmpty()) {
            return new CandidateScore(0, CandidateStrength.NONE, "No deterministic M&A candidate scoring signals matched.");
        }

        // Accumulate: primary tier at full weight, lower tiers at reduced weight
        int rawScore = matchedHigh.size() * 90
                + matchedMedium.size() * (matchedHigh.isEmpty() ? 60 : 20)
                + matchedLow.size()    * (matchedHigh.isEmpty() && matchedMedium.isEmpty() ? 30 : 10);
        int score = Math.min(rawScore, 200);

        CandidateStrength strength = !matchedHigh.isEmpty() ? CandidateStrength.HIGH
                : !matchedMedium.isEmpty()                  ? CandidateStrength.MEDIUM
                :                                             CandidateStrength.LOW;

        List<String> all = new ArrayList<>(matchedHigh);
        all.addAll(matchedMedium);
        all.addAll(matchedLow);
        return new CandidateScore(score, strength, "Matched " + strength + " signals: " + all + ".");
    }

    /** Remove signals that are substrings of another matched signal (avoid double-counting). */
    private static List<String> dedup(List<String> signals) {
        return signals.stream()
                .filter(s -> signals.stream().noneMatch(other -> other != s && other.contains(s)))
                .toList();
    }

    public record CandidateScore(
            int score,
            CandidateStrength strength,
            String reason
    ) {
    }
}
