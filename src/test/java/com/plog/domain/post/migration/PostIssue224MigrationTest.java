package com.plog.domain.post.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class PostIssue224MigrationTest {

    @Test
    void 빈_데이터베이스에서도_초기_posts_생성_후_V224를_적용한다() throws SQLException {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();

            migrate(postgres);

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                var columns = connection.createStatement().executeQuery("""
                        select column_name
                        from information_schema.columns
                        where table_name = 'posts'
                        """);
                java.util.Set<String> names = new java.util.HashSet<>();
                while (columns.next()) {
                    names.add(columns.getString("column_name"));
                }
                assertThat(names).contains(
                        "post_id", "project_member_id", "title", "content",
                        "is_notice", "noticed_at", "created_at", "updated_at");
            }
        }
    }

    @Test
    void 기존_게시글_제목을_백필하고_NOT_NULL_제약을_적용한다() throws SQLException {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                connection.createStatement().execute("""
                        create table posts (
                            post_id bigint primary key,
                            content text not null,
                            is_notice boolean not null,
                            created_at timestamp not null,
                            updated_at timestamp not null
                        )
                        """);
                connection.createStatement().execute("""
                        insert into posts values (
                            1, '  기존 게시글 본문  ', true,
                            timestamp '2026-01-01 00:00:00', timestamp '2026-01-02 00:00:00'
                        )
                        """);
            }

            migrate(postgres);

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                var row = connection.createStatement()
                        .executeQuery("select title, noticed_at from posts where post_id = 1");
                assertThat(row.next()).isTrue();
                assertThat(row.getString("title")).isEqualTo("기존 게시글 본문");
                assertThat(row.getTimestamp("noticed_at").toLocalDateTime())
                        .isEqualTo(java.time.LocalDateTime.of(2026, 1, 2, 0, 0));

                assertThatThrownBy(() -> connection.createStatement().execute("""
                        insert into posts (
                            post_id, title, content, is_notice, created_at, updated_at
                        ) values (
                            2, null, '본문', false,
                            timestamp '2026-01-01 00:00:00', timestamp '2026-01-01 00:00:00'
                        )
                        """))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    private void migrate(PostgreSQLContainer<?> postgres) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }
}
