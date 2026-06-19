alter table sec_filings
    add column sec_signal_type varchar(64);

alter table sec_filings
    add column sec_signal_priority varchar(32);

alter table sec_filings
    add column sec_signal_summary varchar(1024);

alter table sec_filings
    add column sec_signal_warnings varchar(1024);

alter table sec_filings
    add column manual_review_status varchar(32);

alter table sec_filings
    add column manual_review_reason varchar(64);

alter table sec_filings
    add column manual_review_note varchar(1024);

alter table sec_filings
    add column manual_reviewed_at timestamp with time zone;

update sec_filings
set sec_signal_priority = 'UNKNOWN'
where sec_signal_priority is null;

update sec_filings
set sec_signal_type = 'UNKNOWN'
where sec_signal_type is null;

update sec_filings
set manual_review_status = 'PENDING'
where manual_review_status is null;

create index idx_sec_filings_signal_priority
    on sec_filings (sec_signal_priority);

create index idx_sec_filings_manual_review
    on sec_filings (manual_review_status, manual_reviewed_at desc);
