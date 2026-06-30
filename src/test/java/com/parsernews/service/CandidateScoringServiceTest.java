package com.parsernews.service;

import com.parsernews.persistence.CandidateStrength;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateScoringServiceTest {
    private final CandidateScoringService scoringService = new CandidateScoringService();

    @Test
    void mergerAgreementBecomesHigh() {
        CandidateScoringService.CandidateScore score = scoringService.score(
                "Target enters merger agreement",
                "The company will be acquired by Buyer LLC."
        );

        // "merger agreement" + "acquired by" = 2 × 90 = 180
        assertThat(score.strength()).isEqualTo(CandidateStrength.HIGH);
        assertThat(score.score()).isEqualTo(180);
        assertThat(score.reason()).contains("HIGH");
    }

    @Test
    void strategicAlternativesBecomesMedium() {
        CandidateScoringService.CandidateScore score = scoringService.score(
                "Company announces strategic alternatives",
                "The board is reviewing options."
        );

        assertThat(score.strength()).isEqualTo(CandidateStrength.MEDIUM);
        assertThat(score.score()).isEqualTo(60);
    }

    @Test
    void rumorBecomesLow() {
        CandidateScoringService.CandidateScore score = scoringService.score(
                "Market rumor",
                "The company is exploring sale options after interest from sponsors."
        );

        // "rumor" + "exploring sale" + "interest from" = 3 × 30 = 90, strength stays LOW
        assertThat(score.strength()).isEqualTo(CandidateStrength.LOW);
        assertThat(score.score()).isEqualTo(90);
    }

    @Test
    void nonCandidateBecomesNone() {
        CandidateScoringService.CandidateScore score = scoringService.score(
                "Company reports quarterly update",
                "The company announced product progress."
        );

        assertThat(score.strength()).isEqualTo(CandidateStrength.NONE);
        assertThat(score.score()).isZero();
    }

    @Test
    void roundupArticleDoesNotBecomeHighCandidate() {
        CandidateScoringService.CandidateScore score = scoringService.score(
                "13 Press Releases You Need to See This Week",
                "This weekly roundup includes a company that entered into a definitive agreement."
        );

        assertThat(score.strength()).isEqualTo(CandidateStrength.NONE);
        assertThat(score.score()).isZero();
        assertThat(score.reason()).contains("Roundup/aggregator");
    }

    @Test
    void strongerSignalOverridesWeakerSignal() {
        CandidateScoringService.CandidateScore score = scoringService.score(
                "Company rumor after merger agreement",
                "The company entered into a definitive agreement."
        );

        // "merger agreement" + "definitive agreement" = 2×90 + "rumor"×10 = 190, HIGH dominates
        assertThat(score.strength()).isEqualTo(CandidateStrength.HIGH);
        assertThat(score.score()).isEqualTo(190);
    }
}
