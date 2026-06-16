package com.parsernews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scanner.safety")
public record SafetySettings(
        boolean tradingEnabled,
        boolean brokerIntegrationEnabled,
        boolean walletIntegrationEnabled,
        boolean exchangeIntegrationEnabled,
        boolean realWebParsingEnabled
) {
}
