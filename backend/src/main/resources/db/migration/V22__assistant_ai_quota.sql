ALTER TABLE app_user ADD COLUMN IF NOT EXISTS assistant_ai_usage_date DATE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS assistant_ai_usage_count INTEGER NOT NULL DEFAULT 0;
