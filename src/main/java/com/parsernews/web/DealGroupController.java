package com.parsernews.web;

import com.parsernews.persistence.DealGroupReviewEntity;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.service.DealGroupReviewService;
import com.parsernews.service.DealGroupingService;
import com.parsernews.web.SignalInboxController.UnifiedPriority;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class DealGroupController {
    private final DealGroupingService dealGroupingService;
    private final DealGroupReviewService dealGroupReviewService;

    public DealGroupController(DealGroupingService dealGroupingService, DealGroupReviewService dealGroupReviewService) {
        this.dealGroupingService = dealGroupingService;
        this.dealGroupReviewService = dealGroupReviewService;
    }

    @GetMapping("/api/deal-groups")
    @Transactional(readOnly = true)
    public List<DealGroupingService.DealGroupResponse> dealGroups(
            @RequestParam(required = false) ManualReviewStatus reviewStatus,
            @RequestParam(required = false) UnifiedPriority priority,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return dealGroupingService.groups(reviewStatus, priority, limit);
    }

    @GetMapping("/api/deal-groups/{groupKey}")
    @Transactional(readOnly = true)
    public DealGroupingService.DealGroupResponse dealGroup(@PathVariable String groupKey) {
        return dealGroupingService.group(groupKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found"));
    }

    @PatchMapping("/api/deal-groups/{groupKey}/manual-review")
    @Transactional
    public DealGroupingService.DealGroupResponse updateManualReview(
            @PathVariable String groupKey,
            @RequestBody DealGroupManualReviewRequest request
    ) {
        dealGroupingService.group(groupKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found"));
        DealGroupReviewEntity review = dealGroupReviewService.update(groupKey, request.status(), request.reason(), request.note());
        return dealGroupingService.group(review.getGroupKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found"));
    }

    @GetMapping("/api/deal-groups/{groupKey}/telegram-preview")
    @Transactional(readOnly = true)
    public DealGroupTelegramPreviewResponse telegramPreview(@PathVariable String groupKey) {
        DealGroupingService.DealGroupResponse group = dealGroupingService.group(groupKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found"));
        return new DealGroupTelegramPreviewResponse(
                group.groupKey(),
                group.title(),
                dealGroupingService.formatTelegramPreview(group),
                group.reviewStatus(),
                group.reviewReason(),
                group.groupReviewStored()
        );
    }

    public record DealGroupManualReviewRequest(
            ManualReviewStatus status,
            ManualReviewReason reason,
            String note
    ) {
    }

    public record DealGroupTelegramPreviewResponse(
            String groupKey,
            String title,
            String messageText,
            ManualReviewStatus reviewStatus,
            ManualReviewReason reviewReason,
            boolean groupReviewStored
    ) {
    }
}
