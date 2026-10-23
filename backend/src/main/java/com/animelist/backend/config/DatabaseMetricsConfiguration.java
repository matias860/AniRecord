package com.animelist.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DatabaseMetricsConfiguration {
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    DataSource dataSource(DataSourceProperties properties, DatabaseActivityMetrics metrics) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
        dataSource.setMetricsTrackerFactory(metrics);
        return dataSource;
    }
}
