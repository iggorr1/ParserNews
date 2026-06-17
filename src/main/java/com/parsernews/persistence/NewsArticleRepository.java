package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticleEntity, Long> {
    Optional<NewsArticleEntity> findByUrlHash(String urlHash);

    boolean existsByUrlHash(String urlHash);

    List<NewsArticleEntity> findTop200ByOrderByCollectedAtDesc();
}
