USE anime_list;

ALTER TABLE library_entries
    ADD COLUMN rewatch_count INT NOT NULL DEFAULT 0 AFTER personal_score,
    ADD CONSTRAINT chk_library_rewatch_count CHECK (rewatch_count >= 0);
