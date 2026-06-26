package com.parsernews;

import com.parsernews.service.NewsScannerService;
import com.parsernews.service.SafetyGuardService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class StockScannerApplication implements CommandLineRunner {
    private final NewsScannerService newsScannerService;
    private final SafetyGuardService safetyGuardService;

    public StockScannerApplication(
            NewsScannerService newsScannerService,
            SafetyGuardService safetyGuardService
    ) {
        this.newsScannerService = newsScannerService;
        this.safetyGuardService = safetyGuardService;
    }

    public static void main(String[] args) {
        SpringApplication.run(StockScannerApplication.class, args);
    }

    @Override
    public void run(String... args) {
        safetyGuardService.validateStartupSafety();
        newsScannerService.scan();
    }
}
