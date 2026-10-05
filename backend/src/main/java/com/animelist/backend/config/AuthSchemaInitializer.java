package com.animelist.backend.config;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthSchemaInitializer implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public AuthSchemaInitializer(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;
        }
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS oauth_login_ticket (
                token VARCHAR(64) PRIMARY KEY,
                user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                expires_at TIMESTAMP NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_oauth_ticket_expiry ON oauth_login_ticket(expires_at)");
    }
}
