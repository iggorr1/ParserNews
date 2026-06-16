package com.parsernews.service;

import com.parsernews.config.SafetySettings;
import org.springframework.stereotype.Service;

@Service
public class SafetyGuardService {
    private final SafetySettings safetySettings;

    public SafetyGuardService(SafetySettings safetySettings) {
        this.safetySettings = safetySettings;
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

        if (safetySettings.realWebParsingEnabled()) {
            throw new IllegalStateException(
                    "Unsafe configuration for current MVP: real web parsing is disabled until the internet parser phase is explicitly implemented."
            );
        }

        System.out.println("Safety mode: research/news scanning only. Trading, broker, wallet, exchange, and real web parsing integrations are disabled.");
    }
}
