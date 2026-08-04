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
        )

) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
