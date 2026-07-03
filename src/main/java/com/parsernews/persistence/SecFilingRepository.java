package com.parsernews.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SecFilingRepository extends JpaRepository<SecFilingEntity, Long> {
    boolean existsByAccessionNumber(String accessionNumber);
    Optional<SecFilingEntity> findByAccessionNumber(String accessionNumber);

    /** Returns the subset of the given accession numbers that already exist — one query, for batch dedup. */
    @Query("select f.accessionNumber from SecFilingEntity f where f.accessionNumber in :accessionNumbers")
    Set<String> findExistingAccessionNumbers(@Param("accessionNumbers") Collection<String> accessionNumbers);
    List<SecFilingEntity> findTop100ByOrderByFilingDateDescProcessedAtDesc();
    List<SecFilingEntity> findTop50ByOrderByFilingDateDescProcessedAtDesc();
    List<SecFilingEntity> findTop50ByDocumentFetchedAtIsNullOrderByFilingDateDescProcessedAtDesc();
    List<SecFilingEntity> findTop200ByManualReviewStatusInOrderByManualReviewedAtDesc(List<ManualReviewStatus> statuses);
}
