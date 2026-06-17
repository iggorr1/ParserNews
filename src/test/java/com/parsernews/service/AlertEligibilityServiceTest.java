package com.parsernews.service;

import com.parsernews.persistence.CandidateStrength;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEligibilityServiceTest {
    private final AlertEligibilityService service = new AlertEligibilityService();

    @Test
    void highTrustedCandidateIsEligible() {
        AlertEligibilityService.AlertEligibility eligibility = service.evaluate(
                CandidateStrength.HIGH,
                90,
                "https://www.globenewswire.com/news/test",
                false
        );

        assertThat(eligibility.eligible()).isTrue();
        assertThat(eligibility.reason()).contains("HIGH");
    }

    @Test
    void mediumLowAndNoneAreNotEligible() {
        assertThat(service.evaluate(CandidateStrength.MEDIUM, 60, "https://example.com/news", false).eligible()).isFalse();
        assertThat(service.evaluate(CandidateStrength.LOW, 30, "https://example.com/news", false).eligible()).isFalse();
        assertThat(service.evaluate(CandidateStrength.NONE, 0, "https://example.com/news", false).eligible()).isFalse();
    }

    @Test
    void alreadyQueuedCandidateIsNotEligible() {
        AlertEligibilityService.AlertEligibility eligibility = service.evaluate(
                CandidateStrength.HIGH,
                90,
                "https://example.com/news",
                true
        );

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reason()).contains("already queued");
    }

    @Test
    void untrustedSourceIsNotEligible() {
        AlertEligibilityService.AlertEligibility eligibility = service.evaluate(
                CandidateStrength.HIGH,
                90,
                "https://untrusted.test/news",
                false
        );

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reason()).contains("not trusted");
    }
}
