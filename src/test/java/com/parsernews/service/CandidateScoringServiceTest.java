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

        assertThat(score.strength()).isEqualTo(CandidateStrength.HIGH);
        assertThat(score.score()).isEqualTo(90);
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

        assertThat(score.strength()).isEqualTo(CandidateStrength.LOW);
        assertThat(score.score()).isEqualTo(30);
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
    void strongerSignalOverridesWeakerSignal() {
        CandidateScoringService.CandidateScore score = scoringService.score(
                "Company rumor after merger agreement",
                "The company entered into a definitive agreement."
        );

        assertThat(score.strength()).isEqualTo(CandidateStrength.HIGH);
        assertThat(score.score()).isEqualTo(90);
    }
}
