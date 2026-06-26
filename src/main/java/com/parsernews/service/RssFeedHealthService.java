package com.parsernews.service;

import com.parsernews.persistence.RssFeedHealthEntity;
import com.parsernews.persistence.RssFeedHealthRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class RssFeedHealthService {
    static final int STALE_HOURS = 24;
    static final int ERROR_THRESHOLD = 3;

    private final RssFeedHealthRepository repository;

    public RssFeedHealthService(RssFeedHealthRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordSuccess(String feedUrl) {
        find(feedUrl).recordSuccess();
    }

    @Transactional
    public void recordError(String feedUrl, String message) {
        find(feedUrl).recordError(message);
    }

    @Transactional(readOnly = true)
    public List<FeedHealthSummary> summaries() {
        return repository.findAllByOrderByFeedUrlAsc().stream()
                .map(FeedHealthSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeedHealthSummary> unhealthy() {
        return summaries().stream()
                .filter(s -> s.consecutiveErrors() >= ERROR_THRESHOLD || s.staleSinceHours() >= STALE_HOURS)
                .toList();
    }

    private RssFeedHealthEntity find(String feedUrl) {
        return repository.findByFeedUrl(feedUrl)
                .orElseGet(() -> repository.save(new RssFeedHealthEntity(feedUrl)));
    }

    public record FeedHealthSummary(
            String feedUrl,
            Instant lastSuccessAt,
            Instant lastErrorAt,
            int consecutiveErrors,
            String lastErrorMessage,
            long staleSinceHours
    ) {
        static FeedHealthSummary from(RssFeedHealthEntity entity) {
            long stale = entity.getLastSuccessAt() == null
                    ? Long.MAX_VALUE
                    : java.time.Duration.between(entity.getLastSuccessAt(), Instant.now()).toHours();
            return new FeedHealthSummary(
                    entity.getFeedUrl(),
                    entity.getLastSuccessAt(),
                    entity.getLastErrorAt(),
                    entity.getConsecutiveErrors(),
                    entity.getLastErrorMessage(),
                    stale
            );
        }

        public boolean isUnhealthy() {
            return consecutiveErrors >= ERROR_THRESHOLD || staleSinceHours >= STALE_HOURS;
        }
    }
}
