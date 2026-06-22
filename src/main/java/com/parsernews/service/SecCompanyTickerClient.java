package com.parsernews.service;

import java.io.IOException;

public interface SecCompanyTickerClient {
    String fetchCompanyTickerJson() throws IOException, InterruptedException;
}
