ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS notification_catchup_anchor_id INTEGER;
