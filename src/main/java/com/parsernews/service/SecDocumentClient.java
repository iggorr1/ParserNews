package com.parsernews.service;

import java.io.IOException;

public interface SecDocumentClient {
    String fetchDocumentText(String documentUrl) throws IOException, InterruptedException;
}
