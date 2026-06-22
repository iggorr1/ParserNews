alter table detected_events
    add column target_cik varchar(16);

alter table detected_events
    add column target_public_company boolean default false;

alter table detected_events
    add column target_match_confidence varchar(32);

alter table detected_events
    add column buyer_ticker varchar(32);

alter table detected_events
    add column buyer_cik varchar(16);

alter table detected_events
    add column buyer_public_company boolean default false;

alter table detected_events
    add column buyer_match_confidence varchar(32);

alter table detected_events
    add column company_enrichment_warnings varchar(1024);
