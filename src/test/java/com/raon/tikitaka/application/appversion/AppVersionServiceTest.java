package com.raon.tikitaka.application.appversion;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.raon.tikitaka.application.appversion.out.AppVersionSettingPort;
import com.raon.tikitaka.application.appversion.view.AppVersionCheckView;
import com.raon.tikitaka.domain.appversion.AppVersionSetting;
import com.raon.tikitaka.domain.enums.AppPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 설정 행은 메모리 가짜 포트로, 시간은 조작 가능한 Clock으로 준다.
 */
class AppVersionServiceTest {

    private static final String PLAY = "https://store.example.com/android/tikitaka";
    private static final String APP_STORE = "https://store.example.com/ios/tikitaka";

    private final FakeSettingPort port = new FakeSettingPort();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-20T00:00:00Z"));
    private AppVersionService service;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        service = new AppVersionService(port, clock);
        logger = (Logger) LoggerFactory.getLogger(AppVersionService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private static AppVersionSetting row(AppPlatform platform, String latest, String min, String storeUrl) {
        return AppVersionSetting.of(platform, latest, min, storeUrl);
    }

    private void android(String latest, String min, String storeUrl) {
        port.rows = List.of(row(AppPlatform.ANDROID, latest, min, storeUrl), row(AppPlatform.IOS, null, null, null));
    }

    private List<String> logs(Level level) {
        return appender.list.stream().filter(e -> e.getLevel() == level).map(ILoggingEvent::getFormattedMessage).toList();
    }

    // ===== 판정 경계 =====

    @ParameterizedTest
    @CsvSource({
            "0.0.1, FORCE",
            "1.1.9, FORCE",
            "1.2.0, OPTIONAL",   // current == min
            "1.2, OPTIONAL",     // 1.2 == 1.2.0
            "1.3.9, OPTIONAL",
            "1.4.0, NONE",       // current == latest
            "1.4, NONE",
            "1.4.1, NONE",       // 심사 중 빌드처럼 최신보다 높음
            "1.10.0, NONE"       // 문자열 비교면 1.10.0 < 1.4.0 이 된다
    })
    void 최소_지원_버전_미만은_FORCE_최신_미만은_OPTIONAL_그_이상은_NONE(String current, UpdateType expected) {
        android("1.4.0", "1.2.0", PLAY);

        AppVersionCheckView view = service.check("android", current);

        assertThat(view.updateType()).isEqualTo(expected);
        assertThat(view.currentVersion()).isEqualTo(current);
        assertThat(view.latestVersion()).isEqualTo("1.4.0");
        assertThat(view.minSupportedVersion()).isEqualTo("1.2.0");
        assertThat(view.storeUrl()).isEqualTo(expected == UpdateType.NONE ? null : PLAY);
    }

    @Test
    void latest가_비어_있으면_꺼진_것이라_항상_NONE이고_버전_키가_없다() {
        android(null, "1.2.0", PLAY);

        AppVersionCheckView view = service.check("android", "0.0.1");

        assertThat(view.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(view.latestVersion()).isNull();
        assertThat(view.minSupportedVersion()).isNull();
        assertThat(view.storeUrl()).isNull();
        assertThat(service.loggedWarnings()).anyMatch(w -> w.contains("app_version(ANDROID).latest_version이 비어"));
    }

    @Test
    void 모든_값이_비어_있는_행만_있으면_경고_없이_NONE() {
        port.rows = List.of(row(AppPlatform.ANDROID, null, null, null), row(AppPlatform.IOS, null, null, null));

        assertThat(service.check("android", "0.0.1").updateType()).isEqualTo(UpdateType.NONE);
        assertThat(service.check("ios", "0.0.1").updateType()).isEqualTo(UpdateType.NONE);
        assertThat(service.loggedWarnings()).isEmpty();
        assertThat(logs(Level.WARN)).isEmpty();
    }

    @Test
    void 행이_없는_플랫폼은_에러가_아니라_NONE() {
        port.rows = List.of(row(AppPlatform.ANDROID, "1.4.0", "1.2.0", PLAY));

        AppVersionCheckView ios = service.check("ios", "0.0.1");
        assertThat(ios.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(ios.latestVersion()).isNull();

        port.rows = List.of();
        clock.advance(AppVersionService.CACHE_TTL);
        assertThat(service.check("android", "0.0.1").updateType()).isEqualTo(UpdateType.NONE);
    }

    @Test
    void min이_비어_있으면_FORCE_없이_OPTIONAL만_나간다() {
        android("1.4.0", null, PLAY);

        AppVersionCheckView view = service.check("android", "0.0.1");

        assertThat(view.updateType()).isEqualTo(UpdateType.OPTIONAL);
        assertThat(view.minSupportedVersion()).isNull();
        assertThat(view.storeUrl()).isEqualTo(PLAY);
        assertThat(service.loggedWarnings()).isEmpty();
    }

    @Test
    void min이_latest보다_크면_강제는_무시하고_OPTIONAL만_판정하며_경고를_남긴다() {
        android("1.4.0", "2.0.0", PLAY);

        assertThat(service.check("android", "1.0.0").updateType()).isEqualTo(UpdateType.OPTIONAL);
        assertThat(service.check("android", "1.4.0").updateType()).isEqualTo(UpdateType.NONE);
        // 적용하지 않는 값이라 응답에도 싣지 않는다
        assertThat(service.check("android", "1.0.0").minSupportedVersion()).isNull();
        assertThat(service.loggedWarnings()).singleElement().asString()
                .contains("min_supported_version(2.0.0)", "latest_version(1.4.0)");
    }

    @Test
    void min과_latest가_같으면_정상_설정이라_그_아래는_FORCE() {
        android("1.4.0", "1.4.0", PLAY);

        assertThat(service.check("android", "1.3.9").updateType()).isEqualTo(UpdateType.FORCE);
        assertThat(service.check("android", "1.4.0").updateType()).isEqualTo(UpdateType.NONE);
        assertThat(service.loggedWarnings()).isEmpty();
    }

    @Test
    void store_url이_비어_있으면_업데이트가_필요해도_NONE으로_낮춘다() {
        android("1.4.0", "1.2.0", null);

        AppVersionCheckView force = service.check("android", "1.0.0");
        AppVersionCheckView optional = service.check("android", "1.3.0");

        assertThat(force.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(force.storeUrl()).isNull();
        assertThat(optional.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(service.loggedWarnings()).singleElement().asString().contains("app_version(ANDROID).store_url이 비어");
    }

    @Test
    void 공백뿐인_값은_비어_있는_것으로_보고_앞뒤_공백은_지운다() {
        android("  1.4.0 ", "   ", " " + PLAY + " ");

        AppVersionCheckView view = service.check("android", "1.0.0");

        assertThat(view.updateType()).isEqualTo(UpdateType.OPTIONAL);
        assertThat(view.latestVersion()).isEqualTo("1.4.0");
        assertThat(view.minSupportedVersion()).isNull();
        assertThat(view.storeUrl()).isEqualTo(PLAY);
        assertThat(service.loggedWarnings()).isEmpty();
    }

    // ===== 플랫폼 분리 =====

    @Test
    void ios와_android_설정이_서로_섞이지_않는다() {
        port.rows = List.of(row(AppPlatform.IOS, "2.0.0", "1.9.0", APP_STORE),
                row(AppPlatform.ANDROID, "1.4.0", "1.2.0", PLAY));

        AppVersionCheckView ios = service.check("ios", "1.5.0");
        AppVersionCheckView android = service.check("android", "1.5.0");

        assertThat(ios.updateType()).isEqualTo(UpdateType.FORCE);
        assertThat(ios.latestVersion()).isEqualTo("2.0.0");
        assertThat(ios.minSupportedVersion()).isEqualTo("1.9.0");
        assertThat(ios.storeUrl()).isEqualTo(APP_STORE);
        assertThat(android.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(android.latestVersion()).isEqualTo("1.4.0");
        assertThat(android.storeUrl()).isNull();
    }

    @Test
    void ios_값이_없으면_android_설정이_있어도_ios는_NONE이고_android_값이_섞이지_않는다() {
        android("1.4.0", "1.2.0", PLAY);

        AppVersionCheckView ios = service.check("ios", "1.0.0");

        assertThat(ios.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(ios.latestVersion()).isNull();
        assertThat(ios.minSupportedVersion()).isNull();
        assertThat(ios.storeUrl()).isNull();
    }

    @Test
    void 플랫폼은_대소문자를_무시한다() {
        android("1.4.0", "1.2.0", PLAY);

        assertThat(service.check("ANDROID", "1.0.0").updateType()).isEqualTo(UpdateType.FORCE);
        assertThat(service.check("Android", "1.0.0").updateType()).isEqualTo(UpdateType.FORCE);
        assertThat(service.check("iOS", "1.0.0").updateType()).isEqualTo(UpdateType.NONE);
    }

    // ===== 형식 오류(DB 제약이 막지만 방어) =====

    @Test
    void latest_형식이_틀리면_그_플랫폼만_꺼지고_경고를_남긴다() {
        port.rows = List.of(row(AppPlatform.IOS, "2.0.0", null, APP_STORE),
                row(AppPlatform.ANDROID, "1.4.0.1", "1.2.0", PLAY));

        AppVersionCheckView view = service.check("android", "1.0.0");

        assertThat(view.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(view.latestVersion()).isNull();
        assertThat(view.minSupportedVersion()).isNull();
        assertThat(service.loggedWarnings()).singleElement().asString()
                .contains("app_version(ANDROID).latest_version 형식", "1.4.0.1");
        // 다른 플랫폼은 영향이 없다
        assertThat(service.check("ios", "1.0.0").updateType()).isEqualTo(UpdateType.OPTIONAL);
    }

    @Test
    void min_형식이_틀려도_그_플랫폼은_꺼지고_경고를_남긴다() {
        android("1.4.0", "abc", PLAY);

        assertThat(service.check("android", "0.0.1").updateType()).isEqualTo(UpdateType.NONE);
        assertThat(service.loggedWarnings()).singleElement().asString()
                .contains("app_version(ANDROID).min_supported_version 형식", "abc");
    }

    // ===== 캐시 =====

    @Test
    void 첫_요청_전에는_DB를_읽지_않는다() {
        android("1.4.0", "1.2.0", PLAY);

        assertThat(port.calls.get()).isZero();

        service.check("android", "1.0.0");
        assertThat(port.calls.get()).isEqualTo(1);
    }

    @Test
    void TTL_안에서는_포트를_한_번만_읽는다() {
        android("1.4.0", "1.2.0", PLAY);

        service.check("android", "1.0.0");
        clock.advance(Duration.ofSeconds(59));
        service.check("android", "1.3.0");
        service.check("ios", "1.0.0");

        assertThat(port.calls.get()).isEqualTo(1);
    }

    @Test
    void TTL이_지나면_다시_읽어서_바뀐_값이_반영된다() {
        android(null, null, null);
        assertThat(service.check("android", "1.1.0").updateType()).isEqualTo(UpdateType.NONE);

        android("1.4.0", "1.2.0", PLAY);
        clock.advance(Duration.ofSeconds(59));
        assertThat(service.check("android", "1.1.0").updateType()).isEqualTo(UpdateType.NONE);

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.check("android", "1.1.0").updateType()).isEqualTo(UpdateType.FORCE);
        assertThat(port.calls.get()).isEqualTo(2);
    }

    @Test
    void 갱신이_실패하면_마지막_정상_스냅샷을_유지하고_error_로그는_시도마다_한_번() {
        android("1.4.0", "1.2.0", PLAY);
        assertThat(service.check("android", "1.1.0").updateType()).isEqualTo(UpdateType.FORCE);

        port.failure = new IllegalStateException("relation \"app_version\" does not exist");
        clock.advance(AppVersionService.CACHE_TTL);
        for (int i = 0; i < 5; i++) {
            assertThat(service.check("android", "1.1.0").updateType()).isEqualTo(UpdateType.FORCE);
        }

        assertThat(port.calls.get()).isEqualTo(2);
        assertThat(logs(Level.ERROR)).singleElement().asString().contains("마지막 정상");
    }

    @Test
    void 첫_로드부터_실패하면_500이_아니라_모든_플랫폼이_NONE() {
        port.failure = new IllegalStateException("connection refused");

        AppVersionCheckView view = service.check("android", "0.0.1");

        assertThat(view.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(view.latestVersion()).isNull();
        assertThat(service.check("ios", "0.0.1").updateType()).isEqualTo(UpdateType.NONE);
        assertThat(logs(Level.ERROR)).singleElement().asString().contains("꺼진 상태(NONE)");
    }

    @Test
    void 실패한_뒤_다음_시도는_TTL_뒤에_하고_복구되면_새_값을_쓴다() {
        port.failure = new IllegalStateException("connection refused");
        service.check("android", "1.1.0");

        clock.advance(Duration.ofSeconds(59));
        service.check("android", "1.1.0");
        service.check("android", "1.1.0");
        assertThat(port.calls.get()).isEqualTo(1);

        port.failure = null;
        android("1.4.0", "1.2.0", PLAY);
        clock.advance(Duration.ofSeconds(1));
        assertThat(service.check("android", "1.1.0").updateType()).isEqualTo(UpdateType.FORCE);
        assertThat(port.calls.get()).isEqualTo(2);
        assertThat(logs(Level.ERROR)).hasSize(1);
    }

    @Test
    void 경고는_목록이_바뀔_때만_남고_같은_문제로_반복되지_않는다() {
        android("1.4.0", "2.0.0", PLAY);
        service.check("android", "1.0.0");
        for (int i = 0; i < 3; i++) {
            clock.advance(AppVersionService.CACHE_TTL);
            service.check("android", "1.0.0");
        }
        assertThat(port.calls.get()).isEqualTo(4);
        assertThat(logs(Level.WARN)).hasSize(1);

        // 문제가 바뀌면 새 목록을 남긴다
        android("1.4.0", "1.2.0", null);
        clock.advance(AppVersionService.CACHE_TTL);
        service.check("android", "1.0.0");
        assertThat(logs(Level.WARN)).hasSize(2);
        assertThat(logs(Level.WARN).get(1)).contains("store_url이 비어");

        // 해소되면 로그를 남기지 않는다
        android("1.4.0", "1.2.0", PLAY);
        clock.advance(AppVersionService.CACHE_TTL);
        service.check("android", "1.0.0");
        assertThat(logs(Level.WARN)).hasSize(2);
        assertThat(service.loggedWarnings()).isEmpty();

        // 같은 문제가 다시 생기면 다시 남긴다
        android("1.4.0", "1.2.0", null);
        clock.advance(AppVersionService.CACHE_TTL);
        service.check("android", "1.0.0");
        assertThat(logs(Level.WARN)).hasSize(3);
    }

    @Test
    void 요청_처리_중에는_warn_로그를_남기지_않는다() {
        android("1.4.0", "1.2.0", null);
        service.check("android", "1.0.0");
        int warnsAfterLoad = logs(Level.WARN).size();

        for (int i = 0; i < 10; i++) {
            service.check("android", "1.0.0");
        }

        assertThat(logs(Level.WARN)).hasSize(warnsAfterLoad);
    }

    @Test
    void 만료_뒤_동시에_요청이_몰려도_DB_조회는_한_번이다() throws Exception {
        android("1.4.0", "1.2.0", PLAY);
        service.check("android", "1.0.0");
        clock.advance(AppVersionService.CACHE_TTL);

        CountDownLatch inQuery = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        port.onQuery = () -> {
            inQuery.countDown();
            await(release);
        };

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            Future<UpdateType> refresher = pool.submit(() -> service.check("android", "1.0.0").updateType());
            assertThat(inQuery.await(5, TimeUnit.SECONDS)).isTrue();

            // 갱신이 끝나지 않은 동안 다른 요청은 기다리지 않고 직전 값을 쓴다
            List<Future<UpdateType>> others = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                others.add(pool.submit(() -> service.check("android", "1.0.0").updateType()));
            }
            for (Future<UpdateType> other : others) {
                assertThat(other.get(5, TimeUnit.SECONDS)).isEqualTo(UpdateType.FORCE);
            }

            release.countDown();
            assertThat(refresher.get(5, TimeUnit.SECONDS)).isEqualTo(UpdateType.FORCE);
        } finally {
            pool.shutdownNow();
        }
        assertThat(port.calls.get()).isEqualTo(2);
    }

    @Test
    void 첫_로드에_동시에_요청이_몰려도_DB_조회는_한_번이다() throws Exception {
        android("1.4.0", "1.2.0", PLAY);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<UpdateType>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> {
                    await(start);
                    return service.check("android", "1.0.0").updateType();
                }));
            }
            start.countDown();
            for (Future<UpdateType> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(UpdateType.FORCE);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(port.calls.get()).isEqualTo(1);
    }

    // ===== 요청 검증 =====

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"web", "windows", " ios", "ios ", "i0s"})
    void 지원하지_않는_플랫폼이면_400(String platform) {
        android("1.4.0", "1.2.0", PLAY);

        assertThatThrownBy(() -> service.check(platform, "1.0.0"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("지원하지 않는 플랫폼입니다.");
                });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "1.2.3.4", "-1", "1234567"})
    void 앱_버전_형식이_틀리면_400(String currentVersion) {
        android("1.4.0", "1.2.0", PLAY);

        assertThatThrownBy(() -> service.check("android", currentVersion))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("앱 버전 형식이 올바르지 않습니다.");
                });
    }

    @Test
    void 플랫폼과_버전이_둘_다_틀리면_플랫폼_오류가_먼저고_DB를_읽지_않는다() {
        android("1.4.0", "1.2.0", PLAY);

        assertThatThrownBy(() -> service.check("web", "abc"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getReason()).isEqualTo("지원하지 않는 플랫폼입니다."));
        assertThat(port.calls.get()).isZero();
    }

    // ===== 가짜 =====

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class FakeSettingPort implements AppVersionSettingPort {
        volatile List<AppVersionSetting> rows = List.of();
        volatile RuntimeException failure;
        volatile Runnable onQuery = () -> { };
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public List<AppVersionSetting> findAll() {
            calls.incrementAndGet();
            onQuery.run();
            if (failure != null) {
                throw failure;
            }
            return rows;
        }
    }

    private static class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
