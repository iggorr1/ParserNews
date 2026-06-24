package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DealGroupAiReviewRepository extends JpaRepository<DealGroupAiReviewEntity, Long> {
    Optional<DealGroupAiReviewEntity> findTopByGroupKeyOrderByCreatedAtDesc(String groupKey);

    boolean existsByGroupKey(String groupKey);

    List<DealGroupAiReviewEntity> findTop10ByOrderByCreatedAtDesc();
}
