package com.raon.tikitaka.application.appversion;

import com.raon.tikitaka.application.appversion.view.AppVersionCheckView;
import com.raon.tikitaka.global.config.AppVersionProperties;
import com.raon.tikitaka.global.config.AppVersionProperties.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppVersionServiceTest {

    private static final String PLAY = "https://play.google.com/store/apps/details?id=com.tikitaka.raon";
    private static final String APP_STORE = "https://apps.apple.com/app/id0000000000";
    private static final Platform EMPTY = new Platform(null, null, null);

    private static AppVersionService service(Platform ios, Platform android) {
        return new AppVersionService(new AppVersionProperties(ios, android));
    }

    private static AppVersionService android(String latest, String min, String storeUrl) {
        return service(EMPTY, new Platform(latest, min, storeUrl));
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
        AppVersionCheckView view = android("1.4.0", "1.2.0", PLAY).check("android", current);

        assertThat(view.updateType()).isEqualTo(expected);
        assertThat(view.currentVersion()).isEqualTo(current);
        assertThat(view.latestVersion()).isEqualTo("1.4.0");
        assertThat(view.minSupportedVersion()).isEqualTo("1.2.0");
        assertThat(view.storeUrl()).isEqualTo(expected == UpdateType.NONE ? null : PLAY);
    }

    @Test
    void latest가_비어_있으면_꺼진_것이라_항상_NONE이고_버전_키가_없다() {
        AppVersionService service = android(null, "1.2.0", PLAY);

        AppVersionCheckView view = service.check("android", "0.0.1");

        assertThat(view.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(view.latestVersion()).isNull();
        assertThat(view.minSupportedVersion()).isNull();
        assertThat(view.storeUrl()).isNull();
        assertThat(service.configWarnings()).anyMatch(w -> w.contains("android.latest가 비어"));
    }

    @Test
    void 설정을_하나도_넣지_않으면_경고_없이_NONE() {
        AppVersionService service = service(EMPTY, new Platform(null, null, PLAY));

        assertThat(service.check("android", "0.0.1").updateType()).isEqualTo(UpdateType.NONE);
        assertThat(service.check("ios", "0.0.1").updateType()).isEqualTo(UpdateType.NONE);
        assertThat(service.configWarnings()).isEmpty();
    }

    @Test
    void min이_비어_있으면_FORCE_없이_OPTIONAL만_나간다() {
        AppVersionService service = android("1.4.0", null, PLAY);

        AppVersionCheckView view = service.check("android", "0.0.1");

        assertThat(view.updateType()).isEqualTo(UpdateType.OPTIONAL);
        assertThat(view.minSupportedVersion()).isNull();
        assertThat(view.storeUrl()).isEqualTo(PLAY);
        assertThat(service.configWarnings()).isEmpty();
    }

    @Test
    void min이_latest보다_크면_강제는_무시하고_OPTIONAL만_판정하며_기동_경고를_남긴다() {
        AppVersionService service = android("1.4.0", "2.0.0", PLAY);

        assertThat(service.check("android", "1.0.0").updateType()).isEqualTo(UpdateType.OPTIONAL);
        assertThat(service.check("android", "1.4.0").updateType()).isEqualTo(UpdateType.NONE);
        // 적용하지 않는 값이라 응답에도 싣지 않는다
        assertThat(service.check("android", "1.0.0").minSupportedVersion()).isNull();
        assertThat(service.configWarnings()).singleElement().asString()
                .contains("min-supported(2.0.0)", "latest(1.4.0)");
    }

    @Test
    void min과_latest가_같으면_정상_설정이라_그_아래는_FORCE() {
        AppVersionService service = android("1.4.0", "1.4.0", PLAY);

        assertThat(service.check("android", "1.3.9").updateType()).isEqualTo(UpdateType.FORCE);
        assertThat(service.check("android", "1.4.0").updateType()).isEqualTo(UpdateType.NONE);
        assertThat(service.configWarnings()).isEmpty();
    }

    @Test
    void store_url이_비어_있으면_업데이트가_필요해도_NONE으로_낮춘다() {
        AppVersionService service = android("1.4.0", "1.2.0", null);

        AppVersionCheckView force = service.check("android", "1.0.0");
        AppVersionCheckView optional = service.check("android", "1.3.0");

        assertThat(force.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(force.storeUrl()).isNull();
        assertThat(optional.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(service.configWarnings()).singleElement().asString().contains("android.store-url이 비어");
    }

    @Test
    void 공백뿐인_설정은_비어_있는_것으로_보고_앞뒤_공백은_지운다() {
        AppVersionService service = android("  1.4.0 ", "   ", " " + PLAY + " ");

        AppVersionCheckView view = service.check("android", "1.0.0");

        assertThat(view.updateType()).isEqualTo(UpdateType.OPTIONAL);
        assertThat(view.latestVersion()).isEqualTo("1.4.0");
        assertThat(view.minSupportedVersion()).isNull();
        assertThat(view.storeUrl()).isEqualTo(PLAY);
        assertThat(service.configWarnings()).isEmpty();
    }

    // ===== 플랫폼 분리 =====

    @Test
    void ios와_android_설정이_서로_섞이지_않는다() {
        AppVersionService service = service(
                new Platform("2.0.0", "1.9.0", APP_STORE),
                new Platform("1.4.0", "1.2.0", PLAY));

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
        AppVersionService service = service(EMPTY, new Platform("1.4.0", "1.2.0", PLAY));

        AppVersionCheckView ios = service.check("ios", "1.0.0");

        assertThat(ios.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(ios.latestVersion()).isNull();
        assertThat(ios.minSupportedVersion()).isNull();
        assertThat(ios.storeUrl()).isNull();
    }

    @Test
    void 플랫폼은_대소문자를_무시한다() {
        AppVersionService service = android("1.4.0", "1.2.0", PLAY);

        assertThat(service.check("ANDROID", "1.0.0").updateType()).isEqualTo(UpdateType.FORCE);
        assertThat(service.check("Android", "1.0.0").updateType()).isEqualTo(UpdateType.FORCE);
        assertThat(service.check("iOS", "1.0.0").updateType()).isEqualTo(UpdateType.NONE);
    }

    // ===== 설정 형식 오류 =====

    @Test
    void latest_형식이_틀리면_그_플랫폼은_꺼지고_기동_경고를_남긴다() {
        AppVersionService service = service(new Platform("2.0.0", null, APP_STORE), new Platform("1.4", "1.2.0", PLAY));
        AppVersionService broken = service(new Platform("2.0.0", null, APP_STORE), new Platform("1.4.0.1", "1.2.0", PLAY));

        assertThat(service.check("android", "1.0.0").updateType()).isEqualTo(UpdateType.FORCE);

        AppVersionCheckView view = broken.check("android", "1.0.0");
        assertThat(view.updateType()).isEqualTo(UpdateType.NONE);
        assertThat(view.latestVersion()).isNull();
        assertThat(view.minSupportedVersion()).isNull();
        assertThat(broken.configWarnings()).singleElement().asString().contains("android.latest 형식", "1.4.0.1");
        // 다른 플랫폼은 영향이 없다
        assertThat(broken.check("ios", "1.0.0").updateType()).isEqualTo(UpdateType.OPTIONAL);
    }

    @Test
    void min_형식이_틀려도_그_플랫폼은_꺼지고_기동_경고를_남긴다() {
        AppVersionService service = android("1.4.0", "abc", PLAY);

        assertThat(service.check("android", "0.0.1").updateType()).isEqualTo(UpdateType.NONE);
        assertThat(service.configWarnings()).singleElement().asString().contains("android.min-supported 형식", "abc");
    }

    @Test
    void 기동_경고는_로그_메서드를_여러_번_불러도_판정에는_영향이_없다() {
        AppVersionService service = android("1.4.0", "abc", PLAY);
        service.logConfigWarnings();

        assertThat(service.check("android", "0.0.1").updateType()).isEqualTo(UpdateType.NONE);
        assertThat(service.configWarnings()).hasSize(1);
    }

    // ===== 요청 검증 =====

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"web", "windows", " ios", "ios ", "i0s"})
    void 지원하지_않는_플랫폼이면_400(String platform) {
        AppVersionService service = android("1.4.0", "1.2.0", PLAY);

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
        AppVersionService service = android("1.4.0", "1.2.0", PLAY);

        assertThatThrownBy(() -> service.check("android", currentVersion))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("앱 버전 형식이 올바르지 않습니다.");
                });
    }

    @Test
    void 플랫폼과_버전이_둘_다_틀리면_플랫폼_오류가_먼저다() {
        AppVersionService service = android("1.4.0", "1.2.0", PLAY);

        assertThatThrownBy(() -> service.check("web", "abc"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getReason()).isEqualTo("지원하지 않는 플랫폼입니다."));
    }
}
