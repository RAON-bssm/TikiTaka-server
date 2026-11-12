package com.raon.tikitaka.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * post_thumbnail 컬럼 마이그레이션 SQL을 실제 PostgreSQL에서 돌려 본다.
 * 엔티티가 만든 컬럼 정의를 기록해 두고 컬럼을 지워 옛 스키마로 되돌린 뒤 SQL을 두 번 실행해서,
 * 컬럼이 엔티티가 만든 것과 같은 정의(타입, 길이, NULL 허용)로 생기는지 확인한다.
 * 정의가 같아야 ddl-auto=update가 서버 기동 때 컬럼을 다시 건드리지 않는다.
 * WithdrawnUserQueryTest와 같이 TEST_DATABASE_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
// SQL 파일이 BEGIN/COMMIT을 직접 쓰므로 테스트 트랜잭션 밖에서 실행한다
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostThumbnailMigrationDbTest {

    private static final String COLUMN = "post_thumbnail";
    private static final Path SQL_FILE = Path.of("Claude outputs", "migrate_add_post_thumbnail.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }

    @Autowired DataSource dataSource;

    @Test
    void 옛_스키마에서_두_번_실행해도_엔티티와_같은_정의의_컬럼이_생긴다() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, Object> entityDefinition = definition(jdbc);
        assertThat(entityDefinition)
                .containsEntry("data_type", "character varying")
                .containsEntry("character_maximum_length", 255)
                .containsEntry("is_nullable", "YES");

        // 옛 스키마(컬럼 없음)로 되돌린다
        jdbc.execute("ALTER TABLE post DROP COLUMN " + COLUMN);
        assertThat(columnCount(jdbc)).isZero();

        String sql = Files.readString(SQL_FILE, StandardCharsets.UTF_8);
        runScript(sql);
        runScript(sql);

        assertThat(columnCount(jdbc)).isEqualTo(1);
        assertThat(definition(jdbc)).isEqualTo(entityDefinition);
    }

    @Test
    void SQL_파일에_멱등_추가와_되돌리는_방법이_들어_있다() throws IOException {
        String sql = Files.readString(SQL_FILE, StandardCharsets.UTF_8);

        assertThat(sql).contains("ALTER TABLE post ADD COLUMN IF NOT EXISTS " + COLUMN + " varchar(255);");
        assertThat(sql).contains("ALTER TABLE post DROP COLUMN IF EXISTS " + COLUMN + ";");
    }

    // ===== helpers =====

    /** 파일 그대로(BEGIN/COMMIT과 확인 SELECT 포함) 자동 커밋 연결에서 실행한다 */
    private void runScript(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute(sql);
        }
    }

    private static Map<String, Object> definition(JdbcTemplate jdbc) {
        return jdbc.queryForMap("""
                select data_type, character_maximum_length, is_nullable
                from information_schema.columns
                where table_name = 'post' and column_name = ?
                """, COLUMN);
    }

    private static int columnCount(JdbcTemplate jdbc) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'post' and column_name = ?",
                Integer.class, COLUMN);
        return count == null ? 0 : count;
    }
}
