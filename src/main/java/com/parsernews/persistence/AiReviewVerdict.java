package com.parsernews.persistence;

public enum AiReviewVerdict {
    GOOD_SIGNAL,
    NOT_TRADABLE,
    PRIVATE_COMPANY,
    DUPLICATE_OR_UPDATE,
    LATE_STAGE_UPDATE,
    FALSE_POSITIVE,
    NEEDS_HUMAN_REVIEW,
    UNKNOWN
}
