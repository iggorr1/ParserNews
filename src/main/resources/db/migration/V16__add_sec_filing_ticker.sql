-- Ticker for SEC-sourced deals. EFTS supplies it in display_names ("Company (TICKER) (CIK ...)");
-- carrying it through lets the deal group resolve a price and merger-arb spread for SEC filings.
ALTER TABLE sec_filings ADD COLUMN ticker varchar(32);
