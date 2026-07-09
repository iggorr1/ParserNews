package com.parsernews.service;

import java.io.IOException;
import java.time.LocalDate;

public interface SecFullTextSearchClient {
    /** Returns the raw EFTS JSON for a phrase query, optionally restricted to form types and a date range. */
    String search(String query, String forms, LocalDate startDate, LocalDate endDate) throws IOException, InterruptedException;
}
