package com.parsernews.service;

import com.parsernews.config.SafetySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SafetyGuardService {
    private final SafetySettings safetySettings;
    private final String scannerSource;

    public SafetyGuardService(
            SafetySettings safetySettings,
            @Value("${scanner.source:mock}") String scannerSource
    ) {
        this.safetySettings = safetySettings;
        this.scannerSource = scannerSource;
    }

    public void validateStartupSafety() {
        if (safetySettings.tradingEnabled()
                || safetySettings.brokerIntegrationEnabled()
                || safetySettings.walletIntegrationEnabled()
                || safetySettings.exchangeIntegrationEnabled()) {
            throw new IllegalStateException(
                    "Unsafe configuration: this project is a news scanner only and must not trade or connect to brokers, wallets, or exchanges."
                );
        }

        if ("rss".equalsIgnoreCase(scannerSource) && !safetySettings.realWebParsingEnabled()) {
            throw new IllegalStateException(
                    "RSS source requires scanner.safety.real-web-parsing-enabled=true because it reads public internet feeds."
            );
        }

        String webParsingStatus = safetySettings.realWebParsingEnabled()
                ? "RSS web parsing is enabled for public news feeds only."
                : "Real web parsing is disabled.";
        System.out.println("Safety mode: research/news scanning only. Trading, broker, wallet, and exchange integrations are disabled. " + webParsingStatus);
    }
}
