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

CREATE TABLE IF NOT EXISTS anilist_connection (
    id TINYINT NOT NULL DEFAULT 1,
    access_token TEXT NOT NULL,
    token_expires_at DATETIME NULL,
    username VARCHAR(50) NOT NULL,
    connected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_anilist_connection_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    anilist_notification_id INT NOT NULL,
    notification_type ENUM('FIRST_EPISODE','RELATED_MEDIA_ADDITION','MEDIA_DATA_CHANGE','MEDIA_MERGE','MEDIA_DELETION') NOT NULL,
    anilist_media_id INT NULL,
    title VARCHAR(255) NULL,
    cover_image VARCHAR(1000) NULL,
    message VARCHAR(1000) NULL,
    episode INT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at DATETIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_anilist (anilist_notification_id),
    KEY idx_notification_occurred (occurred_at),
    KEY idx_notification_read (is_read)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
