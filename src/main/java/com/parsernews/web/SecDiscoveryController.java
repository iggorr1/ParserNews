package com.parsernews.web;

import com.parsernews.service.SecDiscoveryScanner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecDiscoveryController {
    private final SecDiscoveryScanner discoveryScanner;

    public SecDiscoveryController(SecDiscoveryScanner discoveryScanner) {
        this.discoveryScanner = discoveryScanner;
    }

    @PostMapping("/api/sec/discovery/scan")
    public SecDiscoveryScanner.SecDiscoverySummary scan() {
        return discoveryScanner.scan();
    }

    @GetMapping("/api/sec/discovery/status")
    public SecDiscoveryScanner.SecDiscoveryStatus status() {
        return discoveryScanner.status();
    }
}
