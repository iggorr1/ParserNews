create table rss_feed_health (
    id bigserial primary key,
    feed_url varchar(2048) not null unique,
    last_success_at timestamp with time zone,
    last_error_at timestamp with time zone,
    consecutive_errors integer not null default 0,
    last_error_message varchar(1024),
    created_at timestamp with time zone not null
);

create index idx_rss_feed_health_feed_url on rss_feed_health(feed_url);
