ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS notifications_initialized_at TIMESTAMP;

-- Existing accounts already used the previous notification synchronizer. Marking
-- them initialized avoids treating their next notification as historical.
UPDATE app_user
SET notifications_initialized_at = CURRENT_TIMESTAMP
WHERE account_type = 'ANILIST'
  AND notifications_initialized_at IS NULL;
