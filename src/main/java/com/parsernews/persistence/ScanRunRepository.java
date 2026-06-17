package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanRunRepository extends JpaRepository<ScanRunEntity, Long> {
    List<ScanRunEntity> findTop100ByOrderByStartedAtDesc();
}
