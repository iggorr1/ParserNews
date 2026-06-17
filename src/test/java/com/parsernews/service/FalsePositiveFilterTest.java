package com.parsernews.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FalsePositiveFilterTest {
    private final FalsePositiveFilter filter = new FalsePositiveFilter();

    @Test
    void doesNotTreatGenericCompletionOfTransactionAsFalsePositive() {
        String text = "The parties expect completion of the transaction after customary closing conditions.";

        assertThat(filter.isNonBuyoutAcquisition(text)).isFalse();
        assertThat(filter.reasons(text)).doesNotContain("completion of");
    }

    @Test
    void treatsSpecificCompletionContextsAsFalsePositiveReasons() {
        String text = "The company announced completion of public offering, completion of private placement, " +
                "and completion of debt financing.";

        assertThat(filter.isNonBuyoutAcquisition(text)).isTrue();
        assertThat(filter.reasons(text))
                .contains(
                        "completion of public offering",
                        "completion of private placement",
                        "completion of debt financing"
                );
    }
}
