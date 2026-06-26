package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RssFeedHealthRepository extends JpaRepository<RssFeedHealthEntity, Long> {
    Optional<RssFeedHealthEntity> findByFeedUrl(String feedUrl);
    List<RssFeedHealthEntity> findAllByOrderByFeedUrlAsc();
}
