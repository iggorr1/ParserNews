package com.parsernews.web;

import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.service.DealGroupingService;
import com.parsernews.web.SignalInboxController.UnifiedPriority;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class DealGroupController {
    private final DealGroupingService dealGroupingService;

    public DealGroupController(DealGroupingService dealGroupingService) {
        this.dealGroupingService = dealGroupingService;
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
}
