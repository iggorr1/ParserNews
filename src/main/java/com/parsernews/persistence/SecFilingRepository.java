package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecFilingRepository extends JpaRepository<SecFilingEntity, Long> {
    boolean existsByAccessionNumber(String accessionNumber);
    List<SecFilingEntity> findTop100ByOrderByFilingDateDescProcessedAtDesc();
    List<SecFilingEntity> findTop50ByOrderByFilingDateDescProcessedAtDesc();
    List<SecFilingEntity> findTop50ByDocumentFetchedAtIsNullOrderByFilingDateDescProcessedAtDesc();
}
