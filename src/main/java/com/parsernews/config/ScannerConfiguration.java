package com.parsernews.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ConsoleSettings.class,
        RssSettings.class,
        SafetySettings.class
})
public class ScannerConfiguration {
}
