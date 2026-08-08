-- =====================================================
-- Anime List
-- Esquema inicial de la base de datos
-- =====================================================

CREATE DATABASE IF NOT EXISTS anime_list
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE anime_list;


-- =====================================================
-- APP USER
--
-- Perfil único de la aplicación. No requiere login.
-- El CHECK garantiza que solamente pueda existir id = 1.
-- =====================================================

CREATE TABLE IF NOT EXISTS app_user (
    id TINYINT NOT NULL DEFAULT 1,
    display_name VARCHAR(40) NOT NULL DEFAULT 'Mi perfil',
    bio VARCHAR(160) NOT NULL DEFAULT 'Coleccionando historias, un episodio a la vez.',
    avatar_url VARCHAR(1000) DEFAULT NULL,
    banner_url VARCHAR(1000) DEFAULT NULL,
    background_color CHAR(7) NOT NULL DEFAULT '#0a0c14',
    surface_color CHAR(7) NOT NULL DEFAULT '#171b29',
    accent_color CHAR(7) NOT NULL DEFAULT '#a88bff',
    dots_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_app_user_singleton CHECK (id = 1)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;


INSERT IGNORE INTO app_user (id) VALUES (1);

CREATE TABLE IF NOT EXISTS anilist_connection (
    id TINYINT NOT NULL DEFAULT 1,
    access_token TEXT NOT NULL,
    token_expires_at DATETIME DEFAULT NULL,
    username VARCHAR(50) NOT NULL,
    connected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_anilist_connection_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- =====================================================
-- MEDIA
--
-- Información general obtenida desde AniList.
--
-- total_units:
--   ANIME -> cantidad total de episodios
--   MANGA -> cantidad total de capítulos
-- =====================================================

CREATE TABLE IF NOT EXISTS media (
    id BIGINT NOT NULL AUTO_INCREMENT,

    anilist_id INT NOT NULL,

    media_type ENUM(
        'ANIME',
        'MANGA'
    ) NOT NULL,

    title VARCHAR(255) NOT NULL,
    cover_image VARCHAR(1000) DEFAULT NULL,
    banner_image VARCHAR(1000) DEFAULT NULL,
    synopsis TEXT DEFAULT NULL,
    genres TEXT DEFAULT NULL,

    total_units INT DEFAULT NULL,

    release_start_date DATE DEFAULT NULL,
    release_end_date DATE DEFAULT NULL,

    release_status ENUM(
        'FINISHED',
        'RELEASING',
        'NOT_YET_RELEASED',
        'CANCELLED',
        'HIATUS'
    ) DEFAULT NULL,

    release_season ENUM(
        'WINTER',
        'SPRING',
        'SUMMER',
        'FALL'
    ) DEFAULT NULL,

    release_year SMALLINT DEFAULT NULL,

    last_synced_at TIMESTAMP NULL DEFAULT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT uk_media_anilist_id
        UNIQUE (anilist_id),

    CONSTRAINT chk_media_total_units
        CHECK (
            total_units IS NULL
            OR total_units >= 0
        )

) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;


-- =====================================================
-- LIBRARY ENTRIES
--
-- Seguimiento personal de cada anime o manga.
--
-- progress:
--   ANIME -> episodios vistos
--   MANGA -> capítulos leídos
-- =====================================================

CREATE TABLE IF NOT EXISTS library_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,

    media_id BIGINT NOT NULL,

    progress INT NOT NULL DEFAULT 0,

    personal_score DECIMAL(3,1) DEFAULT NULL,

    rewatch_count INT NOT NULL DEFAULT 0,

    tracking_status ENUM(
        'PLANNED',
        'IN_PROGRESS',
        'PAUSED',
        'COMPLETED',
        'DROPPED'
    ) NOT NULL DEFAULT 'PLANNED',

    started_at DATE DEFAULT NULL,
    completed_at DATE DEFAULT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT uk_library_media
        UNIQUE (media_id),

    CONSTRAINT fk_library_media
        FOREIGN KEY (media_id)
        REFERENCES media(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_library_progress
        CHECK (progress >= 0),

    CONSTRAINT chk_library_score
        CHECK (
            personal_score IS NULL
            OR personal_score BETWEEN 0 AND 10
        ),

    CONSTRAINT chk_library_rewatch_count
        CHECK (rewatch_count >= 0)

) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;


-- =====================================================
-- ACTIVITY HISTORY
-- =====================================================

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

CREATE TABLE IF NOT EXISTS app_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    anilist_notification_id INT NOT NULL,
    notification_type ENUM('FIRST_EPISODE','RELATED_MEDIA_ADDITION','MEDIA_DATA_CHANGE','MEDIA_MERGE','MEDIA_DELETION') NOT NULL,
    anilist_media_id INT DEFAULT NULL,
    title VARCHAR(255) DEFAULT NULL,
    cover_image VARCHAR(1000) DEFAULT NULL,
    message VARCHAR(1000) DEFAULT NULL,
    episode INT DEFAULT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at DATETIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_anilist (anilist_notification_id),
    KEY idx_notification_occurred (occurred_at),
    KEY idx_notification_read (is_read)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
