package com.parsernews.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AlertDispatchSettings.class,
        AnalyzerSettings.class,
        ConsoleSettings.class,
        OpenAiAnalysisSettings.class,
        RssSettings.class,
        SafetySettings.class,
        SecDiscoverySettings.class,
        SecScannerSettings.class,
        TelegramAlertSettings.class
})
public class ScannerConfiguration {
}
