package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScanRunRepository extends JpaRepository<ScanRunEntity, Long> {
    List<ScanRunEntity> findTop100ByOrderByStartedAtDesc();
    Optional<ScanRunEntity> findTopByOrderByStartedAtDesc();
}
