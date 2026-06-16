package com.parsernews.model;

public enum EventStatus {
    IGNORED,
    WATCHLIST,
    MANUAL_REVIEW,
    IMPORTANT,
    HIGH_PRIORITY_SIGNAL;

    public boolean isAtLeast(EventStatus minimumStatus) {
        return ordinal() >= minimumStatus.ordinal();
    }
}
