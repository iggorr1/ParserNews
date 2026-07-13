-- Result of the second "AI check" pass that verifies the per-share offer price against the evidence.
-- price_status: VERIFIED / CORRECTED / NOT_A_CASH_PRICE / NO_EVIDENCE (from the checker) or UNVERIFIED
-- (set in code when the checker's number cannot be found verbatim in the source — a hallucination
-- guard). verified_offer_price is the checker's confirmed/corrected number; price_quote is the exact
-- supporting sentence so a human (and the export consumer) can see the grounding.
ALTER TABLE deal_group_ai_reviews ADD COLUMN price_status        varchar(24);
ALTER TABLE deal_group_ai_reviews ADD COLUMN verified_offer_price numeric(12,4);
ALTER TABLE deal_group_ai_reviews ADD COLUMN price_quote         varchar(1024);
