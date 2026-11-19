package com.raon.tikitaka.application.appversion;

import com.raon.tikitaka.application.appversion.in.CheckAppVersionUseCase;
import com.raon.tikitaka.application.appversion.policy.AppVersion;
import com.raon.tikitaka.application.appversion.view.AppVersionCheckView;
import com.raon.tikitaka.global.config.AppVersionProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 앱 버전 확인. 설정은 생성 때 한 번 해석해 두고, 요청마다 DB나 설정 파싱 없이 판정만 한다.
 *
 * 판정 규칙(플랫폼별):
 * - latest가 없으면(기능 꺼짐) 항상 NONE
 * - current >= latest 면 NONE(최신보다 높은 심사 중 빌드도 NONE)
 * - current < min_supported 면 FORCE, 그 외 current < latest 면 OPTIONAL
 * - min_supported가 없거나 latest보다 크면(설정 오류) FORCE 없이 OPTIONAL만 낸다
 * - 업데이트가 필요해도 store_url이 없으면 NONE으로 낮춘다(갈 곳 없는 강제 팝업 방지)
 * - latest나 min_supported의 형식이 틀리면 그 플랫폼은 기능이 꺼진 것으로 본다
 *
 * 설정 문제는 기동 때 경고로 한 번만 남긴다. 인증 없는 공개 API라 요청마다 경고를 찍으면 로그가 넘칠 수 있어서,
 * 판정 중에는 debug 로그만 남긴다.
 */
@Slf4j
@Service
public class AppVersionService implements CheckAppVersionUseCase {

    static final String INVALID_PLATFORM_MESSAGE = "지원하지 않는 플랫폼입니다.";
    static final String INVALID_VERSION_MESSAGE = "앱 버전 형식이 올바르지 않습니다.";

    private final Map<AppPlatform, PlatformPolicy> policies = new EnumMap<>(AppPlatform.class);
    private final List<String> configWarnings = new ArrayList<>();

    public AppVersionService(AppVersionProperties properties) {
        policies.put(AppPlatform.IOS, resolve(AppPlatform.IOS, properties.ios()));
        policies.put(AppPlatform.ANDROID, resolve(AppPlatform.ANDROID, properties.android()));
    }

    /** 생성 때 모아 둔 설정 문제를 기동 로그에 한 번만 남긴다 */
    @PostConstruct
    void logConfigWarnings() {
        configWarnings.forEach(log::warn);
    }

    /** 기동 때 남기는 설정 경고 목록(테스트 확인용) */
    List<String> configWarnings() {
        return Collections.unmodifiableList(configWarnings);
    }

    @Override
    public AppVersionCheckView check(String platform, String currentVersion) {
        AppPlatform appPlatform = AppPlatform.from(platform)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PLATFORM_MESSAGE));
        AppVersion current = AppVersion.tryParse(currentVersion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_VERSION_MESSAGE));

        PlatformPolicy policy = policies.get(appPlatform);
        if (!policy.enabled()) {
            return new AppVersionCheckView(UpdateType.NONE, currentVersion, null, null, null);
        }

        UpdateType type = policy.decide(current);
        if (type != UpdateType.NONE && policy.storeUrl() == null) {
            log.debug("스토어 주소가 없어 업데이트 판정을 NONE으로 낮춤: platform={}, decided={}", appPlatform, type);
            type = UpdateType.NONE;
        }
        String storeUrl = type == UpdateType.NONE ? null : policy.storeUrl();
        return new AppVersionCheckView(type, currentVersion, policy.latestRaw(), policy.minSupportedRaw(), storeUrl);
    }

    // ===== 설정 해석 =====

    private PlatformPolicy resolve(AppPlatform platform, AppVersionProperties.Platform config) {
        String name = platform.name().toLowerCase(Locale.ROOT);
        if (config == null || config.latest() == null) {
            if (config != null && config.minSupported() != null) {
                warn("app-version.%s.latest가 비어 있어 min-supported(%s)가 있어도 %s 버전 확인은 꺼져 있다(항상 NONE)",
                        name, config.minSupported(), name);
            }
            return PlatformPolicy.DISABLED;
        }

        Optional<AppVersion> latest = AppVersion.tryParse(config.latest());
        if (latest.isEmpty()) {
            warn("app-version.%s.latest 형식이 올바르지 않아(%s) %s 버전 확인을 끈다(항상 NONE)",
                    name, config.latest(), name);
            return PlatformPolicy.DISABLED;
        }

        AppVersion minSupported = null;
        String minSupportedRaw = null;
        if (config.minSupported() != null) {
            Optional<AppVersion> parsed = AppVersion.tryParse(config.minSupported());
            if (parsed.isEmpty()) {
                warn("app-version.%s.min-supported 형식이 올바르지 않아(%s) %s 버전 확인을 끈다(항상 NONE)",
                        name, config.minSupported(), name);
                return PlatformPolicy.DISABLED;
            }
            if (latest.get().isLowerThan(parsed.get())) {
                warn("app-version.%s.min-supported(%s)가 latest(%s)보다 커서 강제 업데이트를 무시하고 권장만 판정한다",
                        name, config.minSupported(), config.latest());
            } else {
                minSupported = parsed.get();
                minSupportedRaw = config.minSupported();
            }
        }

        if (config.storeUrl() == null) {
            warn("app-version.%s.store-url이 비어 있어 업데이트가 필요한 버전도 NONE으로 응답한다", name);
        }
        return new PlatformPolicy(latest.get(), config.latest(), minSupported, minSupportedRaw, config.storeUrl());
    }

    private void warn(String format, Object... args) {
        configWarnings.add(String.format(format, args));
    }

    /**
     * 해석이 끝난 플랫폼 설정. latest가 null이면 꺼진 상태다.
     * minSupported는 실제로 적용하는 값만 담는다(형식 오류이거나 latest보다 크면 null).
     */
    private record PlatformPolicy(AppVersion latest, String latestRaw,
                                  AppVersion minSupported, String minSupportedRaw, String storeUrl) {

        static final PlatformPolicy DISABLED = new PlatformPolicy(null, null, null, null, null);

        boolean enabled() {
            return latest != null;
        }

        UpdateType decide(AppVersion current) {
            if (!current.isLowerThan(latest)) {
                return UpdateType.NONE;
            }
            if (minSupported != null && current.isLowerThan(minSupported)) {
                return UpdateType.FORCE;
            }
            return UpdateType.OPTIONAL;
        }
    }
}
