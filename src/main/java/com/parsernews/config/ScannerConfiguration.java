package com.parsernews.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AlertDispatchSettings.class,
        AnalyzerSettings.class,
        ConsoleSettings.class,
        RssSettings.class,
        SafetySettings.class,
        SecScannerSettings.class,
        TelegramAlertSettings.class
})
public class ScannerConfiguration {
}
