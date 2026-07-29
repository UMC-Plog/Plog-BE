package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.mapping.Column;
import org.junit.jupiter.api.Test;

/**
 * Hibernate 가 만드는 스키마가 운영에 수동 적용한 DDL 과 어긋나지 않는지 본다.
 * DB 없이 매핑 메타데이터만 세우므로 로컬에서도 돈다.
 * <p>
 * 이 검증이 없어서 실제로 CI 가 깨졌다. 자바 필드 초기화
 * ({@code = ThumbnailStatus.NONE})는 DDL 에 실리지 않는데 그걸 DB 기본값으로 착각하면,
 * Hibernate 스키마에는 DEFAULT 가 없고 운영에는 있는 상태가 조용히 만들어진다.
 * 그러면 JPA 를 거치지 않는 INSERT(E2E 픽스처의 raw SQL, 수동 이관)가 CI 에서만 깨진다.
 */
class UploadedFileSchemaTest {

    @Test
    void NOT_NULL_썸네일_컬럼은_DB_기본값을_갖는다() {
        assertThat(sqlTypeOf("thumbnail_status"))
                .as("필드 초기화는 DDL 에 안 실린다. DEFAULT 가 없으면 raw INSERT 가 깨진다.")
                .contains("default 'none'");
        assertThat(nullableOf("thumbnail_status")).isFalse();

        assertThat(sqlTypeOf("thumbnail_attempts")).contains("default 0");
        assertThat(nullableOf("thumbnail_attempts")).isFalse();
    }

    /** 운영 DDL 은 smallint 로 넣었다. integer 로 생성되면 환경별 타입이 갈린다. */
    @Test
    void 시도횟수는_운영과_같은_smallint다() {
        assertThat(sqlTypeOf("thumbnail_attempts")).contains("smallint");
    }

    /** nullable 컬럼까지 기본값을 강요하지 않는다 — 없는 것이 정상이다. */
    @Test
    void nullable_썸네일_컬럼은_그대로다() {
        assertThat(nullableOf("thumbnail_key")).isTrue();
        assertThat(nullableOf("thumbnail_at")).isTrue();
    }

    private String sqlTypeOf(String columnName) {
        String sqlType = column(columnName).getSqlType();
        assertThat(sqlType)
                .as("%s 에 columnDefinition 이 없다", columnName)
                .isNotNull();
        return sqlType.toLowerCase(Locale.ROOT);
    }

    private boolean nullableOf(String columnName) {
        return column(columnName).isNullable();
    }

    private Column column(String columnName) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, PostgreSQLDialect.class.getName())
                .build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(UploadedFile.class)
                    .buildMetadata();
            return metadata.getEntityBinding(UploadedFile.class.getName())
                    .getTable()
                    .getColumns().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(columnName))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(columnName + " 컬럼이 매핑에 없다"));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
