package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DealGroupReviewRepository extends JpaRepository<DealGroupReviewEntity, Long> {
    Optional<DealGroupReviewEntity> findByGroupKey(String groupKey);
}
