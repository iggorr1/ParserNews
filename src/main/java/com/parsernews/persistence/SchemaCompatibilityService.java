package com.parsernews.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaCompatibilityService implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public SchemaCompatibilityService(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!isH2()) {
            return;
        }
        alterColumnToVarchar("detected_events", "event_type");
        alterColumnToVarchar("detected_events", "review_status");
        alterColumnToVarchar("detected_events", "validation_status");
    }

    private boolean isH2() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        }
    }

    private void alterColumnToVarchar(String tableName, String columnName) {
        try {
            jdbcTemplate.execute("alter table " + tableName + " alter column " + columnName + " varchar(64)");
        } catch (RuntimeException ignored) {
            // Best-effort compatibility for local H2 databases created by older MVP versions.
        }
    }
}
