package com.parsernews.service;

import com.parsernews.persistence.AiReviewConfidence;
import com.parsernews.persistence.AiReviewVerdict;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;

import java.util.List;

public interface OpenAiAnalysisClient {
    AiReviewResult reviewDealGroup(String model, String apiKey, String prompt, String input);

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
}
