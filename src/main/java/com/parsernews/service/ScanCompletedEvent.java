package com.parsernews.service;

import org.springframework.context.ApplicationEvent;

public class ScanCompletedEvent extends ApplicationEvent {
    public ScanCompletedEvent(Object source) {
        super(source);
    }
}
