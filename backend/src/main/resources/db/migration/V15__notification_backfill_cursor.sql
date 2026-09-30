ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS notification_backfill_page INTEGER;
