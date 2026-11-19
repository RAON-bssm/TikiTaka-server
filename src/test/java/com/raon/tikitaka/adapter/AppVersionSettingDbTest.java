package com.raon.tikitaka.adapter;

import com.raon.tikitaka.adapter.appversion.out.AppVersionSettingPersistenceAdapter;
import com.raon.tikitaka.domain.appversion.AppVersionSetting;
import com.raon.tikitaka.domain.enums.AppPlatform;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * app_version 테이블을 실제 PostgreSQL에서 확인한다. 엔티티로 행을 읽는지, 체크 제약이 운영자의 오타를 막는지,
 * @Immutable이라 서버가 UPDATE하지 않는지, seed/app_version.sql이 엔티티와 같은 테이블을 만들고 여러 번 실행해도 안전한지 본다.
 * 제약 위반은 트랜잭션을 깨뜨리므로 테스트 트랜잭션을 끄고, 매 테스트 뒤에 행을 지운다.
 * WithdrawnUserQueryTest처럼 TEST_DATABASE_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(AppVersionSettingPersistenceAdapter.class)
class AppVersionSettingDbTest {

    private static final Path SEED_FILE = Path.of("seed", "app_version.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }

    @Autowired DataSource dataSource;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;
    @Autowired AppVersionSettingPersistenceAdapter adapter;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from app_version");
    }

    @Test
    void 엔티티가_만든_테이블에_이름_붙은_체크_제약이_생긴다() {
        assertThat(constraints()).containsKeys(
                "ck_app_version_latest_format", "ck_app_version_min_format", "ck_app_version_store_url");
        assertThat(constraints().get("ck_app_version_latest_format"))
                .contains("latest_version IS NULL", "'^[0-9]{1,6}(\\.[0-9]{1,6}){0,2}$'");
        assertThat(constraints().get("ck_app_version_store_url")).contains("'^https?://'");
    }

    @Test
    void 어댑터가_행을_엔티티로_읽는다() {
        jdbc.update("""
                insert into app_version (platform, latest_version, min_supported_version, store_url, updated_at)
                values ('ANDROID', '1.4.0', '1.2.0', 'https://store.example.com/a', now()),
                       ('IOS', null, null, null, now())
                """);

        assertThat(adapter.findAll())
                .extracting(AppVersionSetting::getPlatform, AppVersionSetting::getLatestVersion,
                        AppVersionSetting::getMinSupportedVersion, AppVersionSetting::getStoreUrl)
                .containsExactlyInAnyOrder(
                        tuple(AppPlatform.ANDROID, "1.4.0", "1.2.0", "https://store.example.com/a"),
                        tuple(AppPlatform.IOS, null, null, null));
        assertThat(adapter.findAll()).allSatisfy(row -> assertThat(row.getUpdatedAt()).isNotNull());
    }

    @Test
    void 행이_없으면_빈_목록이다() {
        assertThat(adapter.findAll()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.4.0", "1.2.3.4", "1.4.0-beta", "", " 1.4.0", "1000000.0.0", "1..2"})
    void 버전_형식이_틀리면_DB가_거부한다(String bad) {
        insertRow("ANDROID");

        assertThatThrownBy(() -> jdbc.update("update app_version set latest_version = ? where platform = 'ANDROID'", bad))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_app_version_latest_format");
        assertThatThrownBy(() -> jdbc.update(
                "update app_version set min_supported_version = ? where platform = 'ANDROID'", bad))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_app_version_min_format");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://x", "", "play.google.com/store", "javascript:alert(1)", " https://x"})
    void 스토어_주소가_http로_시작하지_않으면_DB가_거부한다(String bad) {
        insertRow("ANDROID");

        assertThatThrownBy(() -> jdbc.update("update app_version set store_url = ? where platform = 'ANDROID'", bad))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_app_version_store_url");
    }

    @Test
    void NULL과_정상_값은_받아들인다() {
        insertRow("ANDROID");
        insertRow("IOS");

        jdbc.update("""
                update app_version set latest_version = '1.4.0', min_supported_version = '1.2',
                       store_url = 'https://store.example.com/a', updated_at = now() where platform = 'ANDROID'
                """);
        jdbc.update("update app_version set latest_version = '2', store_url = 'http://x' where platform = 'IOS'");
        jdbc.update("""
                update app_version set latest_version = null, min_supported_version = null, store_url = null
                where platform = 'IOS'
                """);
        // min이 latest보다 큰 것은 DB가 막지 않는다(서비스가 경고하고 강제를 무시한다)
        jdbc.update("update app_version set min_supported_version = '9.9.9' where platform = 'ANDROID'");

        assertThat(jdbc.queryForList("select latest_version from app_version order by platform", String.class))
                .containsExactly("1.4.0", null);
    }

    @Test
    void 지원하지_않는_플랫폼_행은_DB가_거부한다() {
        assertThatThrownBy(() -> insertRow("WEB")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRow("android")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void Immutable_엔티티는_값을_바꿔도_UPDATE되지_않는다() {
        jdbc.update("""
                insert into app_version (platform, latest_version, updated_at) values ('ANDROID', '1.4.0', now())
                """);

        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            AppVersionSetting row = em.find(AppVersionSetting.class, AppPlatform.ANDROID);
            ReflectionTestUtils.setField(row, "latestVersion", "9.9.9");
            em.flush();
        });

        assertThat(jdbc.queryForObject(
                "select latest_version from app_version where platform = 'ANDROID'", String.class)).isEqualTo("1.4.0");
    }

    @Test
    void 시드는_서버가_먼저_만든_테이블에서_두_번_실행해도_행_두_개만_남고_기존_값을_덮지_않는다() throws Exception {
        jdbc.update("""
                insert into app_version (platform, latest_version, store_url, updated_at)
                values ('ANDROID', '1.4.0', 'https://store.example.com/a', now())
                """);

        runSeed();
        runSeed();

        assertThat(jdbc.queryForList("select platform, latest_version, min_supported_version, store_url "
                + "from app_version order by platform"))
                .containsExactly(
                        withNulls("platform", "ANDROID", "latest_version", "1.4.0",
                                "store_url", "https://store.example.com/a"),
                        withNulls("platform", "IOS"));
    }

    @Test
    void 시드가_먼저_만든_테이블은_엔티티가_만든_것과_컬럼과_제약이_같다() throws Exception {
        List<Map<String, Object>> entityColumns = columns();
        Map<String, String> entityConstraints = constraints();

        jdbc.execute("drop table app_version");
        runSeed();
        runSeed();

        assertThat(columns()).isEqualTo(entityColumns);
        assertThat(constraints()).isEqualTo(entityConstraints);
        assertThat(jdbc.queryForList("select platform from app_version order by platform", String.class))
                .containsExactly("ANDROID", "IOS");
        assertThat(jdbc.queryForObject("select count(*) from app_version where latest_version is null "
                + "and min_supported_version is null and store_url is null and updated_at is not null", Integer.class))
                .isEqualTo(2);
        // 시드로 만든 테이블도 엔티티로 읽힌다
        assertThat(adapter.findAll()).hasSize(2);
    }

    @Test
    void 시드_파일에_스토어_주소_값이_들어_있지_않다() throws Exception {
        String sql = Files.readString(SEED_FILE, StandardCharsets.UTF_8);
        String body = sql.substring(sql.indexOf("BEGIN;"));

        assertThat(body).doesNotContain("play.google", "apps.apple", "onestore");
        assertThat(body).contains("ON CONFLICT (platform) DO NOTHING", "CREATE TABLE IF NOT EXISTS app_version");
    }

    // ===== helpers =====

    private void insertRow(String platform) {
        jdbc.update("insert into app_version (platform, updated_at) values (?, now())", platform);
    }

    /** 파일 그대로(BEGIN/COMMIT 포함) 자동 커밋 연결에서 실행한다 */
    private void runSeed() throws Exception {
        String sql = Files.readString(SEED_FILE, StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute(sql);
        }
    }

    private Map<String, String> constraints() {
        Map<String, String> result = new java.util.TreeMap<>();
        jdbc.query("select conname, pg_get_constraintdef(oid) as def from pg_constraint "
                        + "where conrelid = 'app_version'::regclass",
                rs -> {
                    result.put(rs.getString("conname"), rs.getString("def"));
                });
        return result;
    }

    private List<Map<String, Object>> columns() {
        return jdbc.queryForList("""
                select column_name, data_type, character_maximum_length, is_nullable, datetime_precision
                from information_schema.columns where table_name = 'app_version' order by column_name
                """);
    }

    private static Map<String, Object> withNulls(String... keyValues) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("platform", null);
        row.put("latest_version", null);
        row.put("min_supported_version", null);
        row.put("store_url", null);
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put(keyValues[i], keyValues[i + 1]);
        }
        return row;
    }
}
