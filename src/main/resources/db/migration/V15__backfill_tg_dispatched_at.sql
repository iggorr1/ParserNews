-- Backfill tg_dispatched_at for groups already reviewed via Telegram buttons.
-- Groups with USEFUL or IGNORED status were manually reviewed after receiving
-- a Telegram notification, so they were clearly dispatched — just before the
-- tg_dispatched_at column existed or before the fix that persisted it.
UPDATE deal_group_reviews
SET tg_dispatched_at = COALESCE(manual_reviewed_at, updated_at, NOW())
WHERE tg_dispatched_at IS NULL
  AND manual_review_status IN ('USEFUL', 'IGNORED');
