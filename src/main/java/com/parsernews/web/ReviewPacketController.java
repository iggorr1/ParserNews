package com.parsernews.web;

import com.parsernews.service.ReviewPacketService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
public class ReviewPacketController {
    private final ReviewPacketService reviewPacketService;

    public ReviewPacketController(ReviewPacketService reviewPacketService) {
        this.reviewPacketService = reviewPacketService;
    }

    @GetMapping(value = "/api/admin/review-packet.md", produces = "text/markdown")
    public ResponseEntity<String> markdown() {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"parsernews-review-packet.md\"")
                .body(reviewPacketService.markdown());
    }

    @GetMapping("/api/admin/review-packet.json")
    public ReviewPacketService.ReviewPacket json() {
        return reviewPacketService.packet();
    }
}
