package com.parsernews.web;

import com.parsernews.service.StockPriceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class StockPriceController {
    private final StockPriceService stockPriceService;

    public StockPriceController(StockPriceService stockPriceService) {
        this.stockPriceService = stockPriceService;
    }

    @GetMapping("/api/price/{ticker}")
    public StockPriceService.PriceResult currentPrice(@PathVariable String ticker) {
        return stockPriceService.currentPrice(ticker)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Price not available for ticker: " + ticker));
    }

    @GetMapping("/api/price/{ticker}/history")
    public StockPriceService.PriceHistory history(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "3mo") String range,
            @RequestParam(required = false) String interval
    ) {
        return stockPriceService.history(ticker, range, interval)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Price history not available for ticker: " + ticker));
    }
}
