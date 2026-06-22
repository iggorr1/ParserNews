package com.parsernews.service;

import com.parsernews.persistence.DealGroupReviewEntity;
import com.parsernews.persistence.DealGroupReviewRepository;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DealGroupReviewServiceTest {
    @Test
    void createsUpdatesAndResetsGroupReview() {
        DealGroupReviewRepository repository = mock(DealGroupReviewRepository.class);
        when(repository.findByGroupKey("target-ticker:APGE")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DealGroupReviewService service = new DealGroupReviewService(repository);

        DealGroupReviewEntity created = service.update(
                "target-ticker:APGE",
                ManualReviewStatus.USEFUL,
                ManualReviewReason.GOOD_SIGNAL,
                "Good grouped evidence"
        );

        assertThat(created.getManualReviewStatus()).isEqualTo(ManualReviewStatus.USEFUL);
        assertThat(created.getManualReviewReason()).isEqualTo(ManualReviewReason.GOOD_SIGNAL);
        assertThat(created.getManualReviewNote()).isEqualTo("Good grouped evidence");
        assertThat(created.getManualReviewedAt()).isNotNull();

        when(repository.findByGroupKey("target-ticker:APGE")).thenReturn(Optional.of(created));
        DealGroupReviewEntity reset = service.update(
                "target-ticker:APGE",
                ManualReviewStatus.PENDING,
                ManualReviewReason.GOOD_SIGNAL,
                "Should clear"
        );

        assertThat(reset.getManualReviewStatus()).isEqualTo(ManualReviewStatus.PENDING);
        assertThat(reset.getManualReviewReason()).isNull();
        assertThat(reset.getManualReviewNote()).isNull();
        assertThat(reset.getManualReviewedAt()).isNull();
    }
}
