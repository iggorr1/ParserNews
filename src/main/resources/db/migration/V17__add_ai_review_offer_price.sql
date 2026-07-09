-- Per-share cash offer price the AI extracted from the deal (target-side, USD). Powers the
-- merger-arb spread for SEC-sourced deals where the price lives in the filing, not the headline.
ALTER TABLE deal_group_ai_reviews ADD COLUMN offer_price numeric(12,4);
