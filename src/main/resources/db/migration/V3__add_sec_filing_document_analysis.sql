alter table sec_filings
    add column document_url varchar(2048);

alter table sec_filings
    add column document_text_snippet varchar(4000);

alter table sec_filings
    add column document_fetched_at timestamp with time zone;

alter table sec_filings
    add column document_fetch_status varchar(32);

alter table sec_filings
    add column document_signal_strength varchar(32);

alter table sec_filings
    add column document_signal_reason varchar(1024);

create index idx_sec_filings_document_fetched_at
    on sec_filings (document_fetched_at);
