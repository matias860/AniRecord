-- Persistent activity timeline used by profile charts and history
CREATE TABLE IF NOT EXISTS activity_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    media_id BIGINT NOT NULL,
    activity_type ENUM('ADDED','UPDATED','PROGRESS','REMOVED') NOT NULL,
    previous_progress INT DEFAULT NULL,
    new_progress INT DEFAULT NULL,
    previous_status ENUM('PLANNED','IN_PROGRESS','PAUSED','COMPLETED','DROPPED') DEFAULT NULL,
    new_status ENUM('PLANNED','IN_PROGRESS','PAUSED','COMPLETED','DROPPED') DEFAULT NULL,
    previous_score DECIMAL(3,1) DEFAULT NULL,
    new_score DECIMAL(3,1) DEFAULT NULL,
    previous_rewatch_count INT DEFAULT NULL,
    new_rewatch_count INT DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_activity_created_at (created_at),
    KEY idx_activity_media (media_id),
    CONSTRAINT fk_activity_media FOREIGN KEY (media_id) REFERENCES media(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO activity_history (
    media_id, activity_type, new_progress, new_status, new_score, new_rewatch_count, created_at
)
SELECT
    le.media_id, 'ADDED', le.progress, le.tracking_status, le.personal_score, le.rewatch_count, le.created_at
FROM library_entries le
WHERE NOT EXISTS (
    SELECT 1 FROM activity_history ah WHERE ah.media_id = le.media_id
);

