package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetectedEventRepository extends JpaRepository<DetectedEventEntity, Long> {
    List<DetectedEventEntity> findTop200ByOrderByDetectedAtDesc();
    boolean existsByArticle(NewsArticleEntity article);
}
