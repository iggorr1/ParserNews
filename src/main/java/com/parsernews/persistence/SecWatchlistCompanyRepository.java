package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecWatchlistCompanyRepository extends JpaRepository<SecWatchlistCompanyEntity, Long> {
    boolean existsByCik(String cik);
    long countByEnabledTrue();
    List<SecWatchlistCompanyEntity> findAllByOrderByCompanyNameAscCikAsc();
    List<SecWatchlistCompanyEntity> findByEnabledTrueOrderByCompanyNameAscCikAsc();
}
