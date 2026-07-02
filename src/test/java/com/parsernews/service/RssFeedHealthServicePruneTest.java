package com.parsernews.service;

import com.parsernews.persistence.RssFeedHealthEntity;
import com.parsernews.persistence.RssFeedHealthRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RssFeedHealthServicePruneTest {

    @Test
    void pruneRemovesRecordsForFeedsNoLongerConfigured() {
        RssFeedHealthRepository repository = mock(RssFeedHealthRepository.class);
        RssFeedHealthEntity keep = new RssFeedHealthEntity("https://keep.example/rss");
        RssFeedHealthEntity orphan = new RssFeedHealthEntity("https://gone.example/rss");
        when(repository.findAllByOrderByFeedUrlAsc()).thenReturn(List.of(keep, orphan));
        RssFeedHealthService service = new RssFeedHealthService(repository);

        int pruned = service.pruneUnconfigured(List.of("https://keep.example/rss"));

        assertThat(pruned).isEqualTo(1);
        verify(repository).deleteAll(argThat((Iterable<RssFeedHealthEntity> it) ->
                it.iterator().next().getFeedUrl().equals("https://gone.example/rss")));
    }

    @Test
    void pruneDoesNothingWhenAllFeedsStillConfigured() {
        RssFeedHealthRepository repository = mock(RssFeedHealthRepository.class);
        RssFeedHealthEntity keep = new RssFeedHealthEntity("https://keep.example/rss");
        when(repository.findAllByOrderByFeedUrlAsc()).thenReturn(List.of(keep));
        RssFeedHealthService service = new RssFeedHealthService(repository);

        int pruned = service.pruneUnconfigured(List.of("https://keep.example/rss"));

        assertThat(pruned).isZero();
        verify(repository, never()).deleteAll(org.mockito.ArgumentMatchers.anyList());
    }
}
