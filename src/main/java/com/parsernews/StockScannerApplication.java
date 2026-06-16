package com.parsernews;

import com.parsernews.service.NewsScannerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StockScannerApplication implements CommandLineRunner {
    private final NewsScannerService newsScannerService;

    public StockScannerApplication(NewsScannerService newsScannerService) {
        this.newsScannerService = newsScannerService;
    }

    public static void main(String[] args) {
        SpringApplication.run(StockScannerApplication.class, args);
    }

    @Override
    public void run(String... args) {
        newsScannerService.scan();
    }
}
