package com.parsernews.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class InMemoryLogAppender extends AppenderBase<ILoggingEvent> {
    private static final int MAX_SIZE = 500;
    private static final Deque<LogEntry> ENTRIES = new ArrayDeque<>();
    private static final Object LOCK = new Object();

    public static List<LogEntry> getEntries() {
        synchronized (LOCK) {
            return new ArrayList<>(ENTRIES);
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            ENTRIES.clear();
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        String logger = event.getLoggerName();
        // skip noisy framework internals
        if (logger.startsWith("org.springframework.boot.web.embedded")
                || logger.startsWith("com.zaxxer.hikari")
                || logger.equals("org.springframework.web.servlet.DispatcherServlet")) {
            return;
        }
        synchronized (LOCK) {
            if (ENTRIES.size() >= MAX_SIZE) {
                ENTRIES.pollFirst();
            }
            // Shorten fully-qualified logger names for readability
            String shortLogger = logger.contains(".")
                    ? logger.substring(logger.lastIndexOf('.') + 1)
                    : logger;
            ENTRIES.addLast(new LogEntry(
                    event.getTimeStamp(),
                    event.getLevel().toString(),
                    shortLogger,
                    event.getFormattedMessage(),
                    event.getThreadName()
            ));
        }
    }

    public record LogEntry(long ts, String level, String logger, String message, String thread) {}
}
