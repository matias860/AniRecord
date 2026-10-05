package com.animelist.backend.config;

import java.sql.Connection;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Keeps the shared update-prompt preference compatible when Flyway is disabled. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class UserPreferenceSchemaInitializer implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPreferenceSchemaInitializer.class);
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public UserPreferenceSchemaInitializer(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;
        }
        jdbc.execute("ALTER TABLE app_user ADD COLUMN IF NOT EXISTS last_sync_prompt_commit VARCHAR(64)");
        Boolean notificationStateExists = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'app_user'
                  AND column_name = 'notifications_initialized_at'
            )
            """, Boolean.class);
        jdbc.execute("ALTER TABLE app_user ADD COLUMN IF NOT EXISTS notifications_initialized_at TIMESTAMP");
        jdbc.execute("ALTER TABLE app_user ADD COLUMN IF NOT EXISTS notification_backfill_page INTEGER");
        jdbc.execute("ALTER TABLE app_user ADD COLUMN IF NOT EXISTS notification_catchup_page INTEGER");
        jdbc.execute("ALTER TABLE app_user ADD COLUMN IF NOT EXISTS notification_catchup_anchor_id INTEGER");
        jdbc.execute("ALTER TABLE app_user ADD COLUMN IF NOT EXISTS notification_backfill_as_read BOOLEAN");
        if (!Boolean.TRUE.equals(notificationStateExists)) {
            jdbc.execute("UPDATE app_user SET notifications_initialized_at = CURRENT_TIMESTAMP WHERE account_type = 'ANILIST' AND notifications_initialized_at IS NULL");
        }
        LOGGER.info("Preferencia compartida del aviso de sincronización verificada correctamente");
    }
}
