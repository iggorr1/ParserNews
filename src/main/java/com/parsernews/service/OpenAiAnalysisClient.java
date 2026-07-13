package com.parsernews.service;

import com.parsernews.persistence.AiReviewConfidence;
import com.parsernews.persistence.AiReviewVerdict;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;

import java.util.List;

public interface OpenAiAnalysisClient {
    AiReviewResult reviewDealGroup(String model, String apiKey, String prompt, String input);

    /**
     * Second, focused "AI check" pass that verifies (or corrects) the per-share offer price against
     * the evidence, grounding it in an exact quote. Runs only for deals that carry a candidate price
     * — the first pass already produced {@code candidatePrice}; this pass double-checks it.
     */
    PriceVerificationResult verifyOfferPrice(
            String model,
            String apiKey,
            String evidence,
            java.math.BigDecimal candidatePrice,
            String targetCompany
    );

    record AiReviewResult(
            AiReviewVerdict verdict,
            AiReviewConfidence confidence,
            boolean tradablePublicTarget,
            ManualReviewStatus suggestedReviewStatus,
            ManualReviewReason suggestedReviewReason,
            String reason,
            List<String> riskFlags,
            String rawJson,
            java.math.BigDecimal offerPricePerShare,
            String targetCompany,
            String acquirerCompany
    ) {
    }

    /**
     * Outcome of the price-verification pass. {@code status} is one of VERIFIED (candidate confirmed),
     * CORRECTED (a different price is right — see {@code verifiedPrice}), NOT_A_CASH_PRICE (no fixed
     * per-share cash consideration, e.g. all-stock/undisclosed) or NO_EVIDENCE (evidence does not
     * state a price). {@code quote} is the exact supporting sentence from the source.
     */
    record PriceVerificationResult(
            String status,
            java.math.BigDecimal verifiedPrice,
            String currency,
            String basis,
            String quote,
            String note,
            String rawJson
    ) {
    }
}
