alter table deal_group_reviews
    add column if not exists tg_dispatched_at timestamp with time zone;
