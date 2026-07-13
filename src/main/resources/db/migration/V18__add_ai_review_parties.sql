-- AI-identified deal parties and SEC-resolved tickers. The AI decides which side is the TARGET
-- (the company being acquired) vs the ACQUIRER, then we resolve each name to a ticker via the
-- authoritative SEC company_tickers list — fixing the target/acquirer confusion and wrong tickers
-- the regex/EFTS grouping produced. ticker_confidence records how strongly the target ticker was
-- resolved (RESOLVED = exact SEC name/ticker match, UNVERIFIED = weak/no match) so the export
-- consumer can decide whether to trust it or send the deal back for a recheck.
ALTER TABLE deal_group_ai_reviews ADD COLUMN target_company    varchar(512);
ALTER TABLE deal_group_ai_reviews ADD COLUMN acquirer_company  varchar(512);
ALTER TABLE deal_group_ai_reviews ADD COLUMN target_ticker     varchar(32);
ALTER TABLE deal_group_ai_reviews ADD COLUMN acquirer_ticker   varchar(32);
ALTER TABLE deal_group_ai_reviews ADD COLUMN ticker_confidence varchar(16);
