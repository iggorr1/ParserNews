package com.parsernews.model;

public enum EventStatus {
    IGNORED,
    WATCHLIST,
    MANUAL_REVIEW,
    IMPORTANT;

    public boolean isAtLeast(EventStatus minimumStatus) {
        return ordinal() >= minimumStatus.ordinal();
    }
}
