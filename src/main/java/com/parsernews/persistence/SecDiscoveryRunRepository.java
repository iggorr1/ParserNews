package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SecDiscoveryRunRepository extends JpaRepository<SecDiscoveryRunEntity, Long> {
    Optional<SecDiscoveryRunEntity> findTopByOrderByStartedAtDesc();
}
