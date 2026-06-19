package com.parsernews.persistence;

public enum ManualReviewReason {
    GOOD_SIGNAL,
    FALSE_POSITIVE,
    PRIVATE_COMPANY,
    NOT_TRADABLE,
    LATE_STAGE_UPDATE,
    LAW_FIRM_OR_SHAREHOLDER_ALERT,
    WRONG_EXTRACTION,
    DUPLICATE_OR_UPDATE,
    OTHER
}
