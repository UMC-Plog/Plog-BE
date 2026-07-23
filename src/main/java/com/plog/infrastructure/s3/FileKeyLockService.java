package com.plog.infrastructure.s3;

import java.util.Collection;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL transaction-scoped lock for coordinating file reference creation and deletion.
 * Call within an active transaction; sorting prevents deadlocks when multiple keys are locked.
 */
@Component
@RequiredArgsConstructor
public class FileKeyLockService {
    private final JdbcTemplate jdbcTemplate;

    public void lockAll(Collection<String> fileKeys) {
        fileKeys.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(this::lock);
    }

    private void lock(String fileKey) {
        jdbcTemplate.query(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                preparedStatement -> preparedStatement.setString(1, fileKey),
                resultSet -> null
        );
    }
}
