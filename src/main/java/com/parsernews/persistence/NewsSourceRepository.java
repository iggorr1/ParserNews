package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsSourceRepository extends JpaRepository<NewsSourceEntity, Long> {
    Optional<NewsSourceEntity> findByName(String name);
}
