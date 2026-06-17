package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DetectedEventRepository extends JpaRepository<DetectedEventEntity, Long> {
    List<DetectedEventEntity> findTop200ByOrderByDetectedAtDesc();
    boolean existsByArticle(NewsArticleEntity article);
    Optional<DetectedEventEntity> findByArticle(NewsArticleEntity article);
    long countByCandidateStrengthNot(CandidateStrength candidateStrength);
    long countByCandidateStrength(CandidateStrength candidateStrength);
    long countByAlertEligibleTrue();
    long countByAlertQueuedAtIsNotNull();
}
