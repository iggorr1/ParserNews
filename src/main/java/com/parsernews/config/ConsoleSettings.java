package com.parsernews.config;

import com.parsernews.model.EventStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scanner")
public record ConsoleSettings(
        EventStatus consoleMinStatus
) {
}
