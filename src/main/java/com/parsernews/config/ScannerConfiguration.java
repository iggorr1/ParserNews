package com.parsernews.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AnalyzerSettings.class,
        ConsoleSettings.class,
        RssSettings.class,
        SafetySettings.class
})
public class ScannerConfiguration {
}
