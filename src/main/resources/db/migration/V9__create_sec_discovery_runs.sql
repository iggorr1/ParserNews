create table sec_discovery_runs (
    id bigserial primary key,
    started_at timestamp with time zone not null,
    finished_at timestamp with time zone,
    status varchar(32) not null,
    scanned_count integer not null default 0,
    new_count integer not null default 0,
    duplicate_count integer not null default 0,
    created_or_updated_group_count integer not null default 0,
    skipped_count integer not null default 0,
    error_count integer not null default 0,
    error_message varchar(2048),
    created_at timestamp with time zone not null
);

create index idx_sec_discovery_runs_started_at on sec_discovery_runs(started_at desc);
