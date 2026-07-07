package com.parsernews.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DealGroupingSpreadTest {

    @Test
    void extractsTenderOfferPricePerShare() {
        String doc = "Offer to Purchase for Cash All Outstanding Shares of Common Stock "
                + "at a purchase price of $23.50 per Share, net to the seller in cash.";
        assertThat(DealGroupingService.extractPerShareUsd(doc)).isEqualByComparingTo(new BigDecimal("23.50"));
    }

    @Test
    void extractsMergerProxyCashPerShare() {
        String doc = "Upon completion of the merger, you will be entitled to receive $18.50 in cash, "
                + "without interest, for each share of common stock you own.";
        assertThat(DealGroupingService.extractPerShareUsd(doc)).isEqualByComparingTo(new BigDecimal("18.50"));
    }

    @Test
    void handlesHyphenatedPerShare() {
        assertThat(DealGroupingService.extractPerShareUsd("acquired in a $3.15-per-share tender offer"))
                .isEqualByComparingTo(new BigDecimal("3.15"));
    }

    @Test
    void fallsBackAcrossTextsAndReturnsNullWhenNoPrice() {
        assertThat(DealGroupingService.extractPerShareUsd(null, "", "no price here")).isNull();
        assertThat(DealGroupingService.extractPerShareUsd("nothing", "$12.00 per share"))
                .isEqualByComparingTo(new BigDecimal("12.00"));
    }
}
