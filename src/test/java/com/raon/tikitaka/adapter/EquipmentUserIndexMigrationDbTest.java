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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 착용 정보 조회 인덱스(idx_equipment_user) 마이그레이션 SQL을 실제 PostgreSQL에서 돌려 본다.
 * 엔티티 @Index로 만든 정의를 기록해 두고 인덱스를 지워 옛 스키마로 되돌린 뒤 SQL을 두 번 실행해서,
 * 인덱스가 하나만 남고 정의(이름, 컬럼)가 엔티티가 만든 것과 같은지 확인한다.
 * 이름이 같고 정의도 같아야 ddl-auto=update가 서버 기동 때 다시 만들지 않는다.
 * PostBoardIndexMigrationDbTest와 같이 TEST_DATABASE_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
// SQL 파일이 BEGIN/COMMIT을 직접 쓰므로 테스트 트랜잭션 밖에서 실행한다
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EquipmentUserIndexMigrationDbTest {

    private static final String INDEX = "idx_equipment_user";
    private static final Path SQL_FILE = Path.of("Claude outputs", "migrate_add_equipment_user_index.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }

    @Autowired DataSource dataSource;

    @Test
    void 옛_스키마에서_두_번_실행해도_엔티티와_같은_정의의_인덱스_하나만_남는다() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String entityDefinition = definition(jdbc);
        assertThat(entityDefinition).isEqualTo(
                "CREATE INDEX idx_equipment_user ON public.equipment USING btree (user_id)");

        // 옛 스키마(인덱스 없음)로 되돌린다
        jdbc.execute("DROP INDEX " + INDEX);
        assertThat(indexNames(jdbc)).doesNotContain(INDEX);

        String sql = Files.readString(SQL_FILE, StandardCharsets.UTF_8);
        runScript(sql);
        runScript(sql);

        assertThat(indexNames(jdbc).stream().filter(INDEX::equals)).hasSize(1);
        assertThat(definition(jdbc)).isEqualTo(entityDefinition);
        // 같은 컬럼 구성의 인덱스가 다른 이름으로 하나 더 생기지 않았다
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_indexes where tablename = 'equipment' and indexdef like '%btree (user_id)'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select indisvalid from pg_index where indexrelid = '" + INDEX + "'::regclass", Boolean.class)).isTrue();
    }

    @Test
    void SQL_파일에_멱등_생성과_되돌리는_방법이_들어_있다() throws IOException {
        String sql = Files.readString(SQL_FILE, StandardCharsets.UTF_8);

        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS " + INDEX + " ON equipment (user_id);");
        assertThat(sql).contains("DROP INDEX IF EXISTS " + INDEX + ";");
        assertThat(sql).contains("CREATE INDEX CONCURRENTLY IF NOT EXISTS " + INDEX);
    }

    // ===== helpers =====

    /** 파일 그대로(BEGIN/COMMIT과 확인 SELECT 포함) 자동 커밋 연결에서 실행한다 */
    private void runScript(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute(sql);
        }
    }

    private static String definition(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select indexdef from pg_indexes where indexname = ?", String.class, INDEX);
    }

    private static List<String> indexNames(JdbcTemplate jdbc) {
        return jdbc.queryForList("select indexname from pg_indexes where tablename = 'equipment'", String.class);
    }
}
