package com.parsernews.service;

import com.parsernews.persistence.CandidateStrength;
import org.springframework.stereotype.Service;

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
        CandidateScore high = match(text, CandidateStrength.HIGH, 90, HIGH_SIGNALS);
        if (high.strength() != CandidateStrength.NONE) {
            return high;
        }
        CandidateScore medium = match(text, CandidateStrength.MEDIUM, 60, MEDIUM_SIGNALS);
        if (medium.strength() != CandidateStrength.NONE) {
            return medium;
        }
        CandidateScore low = match(text, CandidateStrength.LOW, 30, LOW_SIGNALS);
        if (low.strength() != CandidateStrength.NONE) {
            return low;
        }
        return new CandidateScore(0, CandidateStrength.NONE, "No deterministic M&A candidate scoring signals matched.");
    }

    private CandidateScore match(String text, CandidateStrength strength, int score, List<String> signals) {
        for (String signal : signals) {
            if (text.contains(signal)) {
                return new CandidateScore(score, strength, "Matched " + strength + " candidate signal: " + signal + ".");
            }
        }
        return new CandidateScore(0, CandidateStrength.NONE, "");
    }

    public record CandidateScore(
            int score,
            CandidateStrength strength,
            String reason
    ) {
    }
}
