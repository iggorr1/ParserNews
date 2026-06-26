package com.parsernews.service;

import com.parsernews.persistence.DealGroupReviewEntity;
import com.parsernews.persistence.DealGroupReviewRepository;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DealGroupReviewService {
    private final DealGroupReviewRepository repository;

    public DealGroupReviewService(DealGroupReviewRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<DealGroupReviewEntity> find(String groupKey) {
        return repository.findByGroupKey(groupKey);
    }

    @Transactional
    public DealGroupReviewEntity getOrCreate(String groupKey) {
        return repository.findByGroupKey(groupKey)
                .orElseGet(() -> repository.save(new DealGroupReviewEntity(groupKey)));
    }

    @Transactional
    public DealGroupReviewEntity update(String groupKey, ManualReviewStatus status, ManualReviewReason reason, String note) {
        DealGroupReviewEntity review = repository.findByGroupKey(groupKey)
                .orElseGet(() -> new DealGroupReviewEntity(groupKey));
        review.updateManualReview(status, reason, note);
        return repository.save(review);
    }
}
