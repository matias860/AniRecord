USE anime_list;

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
    notification_type ENUM('FIRST_EPISODE','AIRING_EPISODE','RELATED_MEDIA_ADDITION','MEDIA_DATA_CHANGE','MEDIA_MERGE','MEDIA_DELETION') NOT NULL,
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
