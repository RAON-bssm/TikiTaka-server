package com.raon.tikitaka.application.appversion;

import com.raon.tikitaka.application.appversion.in.CheckAppVersionUseCase;
import com.raon.tikitaka.application.appversion.out.AppVersionSettingPort;
import com.raon.tikitaka.application.appversion.policy.AppVersion;
import com.raon.tikitaka.application.appversion.view.AppVersionCheckView;
import com.raon.tikitaka.domain.appversion.AppVersionSetting;
import com.raon.tikitaka.domain.enums.AppPlatform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 앱 버전 확인. 설정은 app_version 테이블(운영자가 DB에서 직접 UPDATE)에서 읽는다.
 *
 * 판정 규칙(플랫폼별):
 * - latest가 없으면(행 없음 포함, 기능 꺼짐) 항상 NONE
 * - current >= latest 면 NONE(최신보다 높은 심사 중 빌드도 NONE)
 * - current < min_supported 면 FORCE, 그 외 current < latest 면 OPTIONAL
 * - min_supported가 없거나 latest보다 크면(설정 오류) FORCE 없이 OPTIONAL만 낸다
 * - 업데이트가 필요해도 store_url이 없으면 NONE으로 낮춘다(갈 곳 없는 강제 팝업 방지)
 * - latest나 min_supported의 형식이 틀리면 그 플랫폼은 꺼진 것으로 본다(DB 제약이 막지만 방어)
 *
 * 캐시: 요청마다 DB를 읽지 않고, 해석한 설정을 메모리 스냅샷으로 들고 있다가 CACHE_TTL(60초)이 지나면 다시 읽는다.
 * - 첫 로드는 첫 요청 때 한다. 기동 때 DB를 읽지 않아 기동 순서나 테이블 생성 시점과 얽히지 않는다
 * - 만료 뒤 갱신은 락을 잡은 한 스레드만 한다. 다른 요청은 기다리지 않고 직전 스냅샷(volatile)을 쓴다.
 *   첫 로드 때는 쓸 스냅샷이 없으므로 락을 기다렸다가 먼저 읽은 결과를 같이 쓴다. 어느 쪽이든 DB 조회는 한 번이다
 * - 조회가 실패하면(연결 오류, 테이블 없음) 500을 내지 않고 마지막 정상 스냅샷을 계속 쓰며, 정상 스냅샷이 없으면
 *   모든 플랫폼을 꺼진 상태로 본다. 다음 시도도 TTL 뒤로 미뤄 DB가 죽어 있는 동안 요청마다 DB를 두드리지 않는다.
 *   실패 로그는 갱신 시도마다 한 번 error로 남긴다
 * - 서버 인스턴스마다 캐시가 따로라 반영 시점이 인스턴스마다 최대 60초 어긋날 수 있다
 *
 * 설정 경고(min이 latest보다 큼, store_url 없음 등)는 스냅샷을 새로 읽을 때 만들고, 직전에 남긴 목록과 다를 때만 warn으로 남긴다.
 * 인증 없는 공개 API라 요청 처리 중에는 debug 로그만 남긴다.
 */
@Slf4j
@Service
public class AppVersionService implements CheckAppVersionUseCase {

    static final String INVALID_PLATFORM_MESSAGE = "지원하지 않는 플랫폼입니다.";
    static final String INVALID_VERSION_MESSAGE = "앱 버전 형식이 올바르지 않습니다.";
    static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final AppVersionSettingPort settingPort;
    private final Clock clock;
    private final ReentrantLock refreshLock = new ReentrantLock();

    /** 마지막으로 만든 스냅샷. 첫 요청 전에는 null이다 */
    private volatile Snapshot snapshot;
    /** 직전에 warn으로 남긴 설정 경고 목록. refreshLock 안에서만 읽고 쓴다 */
    private List<String> loggedWarnings = List.of();

    @Autowired
    public AppVersionService(AppVersionSettingPort settingPort) {
        this(settingPort, Clock.systemUTC());
    }

    AppVersionService(AppVersionSettingPort settingPort, Clock clock) {
        this.settingPort = settingPort;
        this.clock = clock;
    }

    @Override
    public AppVersionCheckView check(String platform, String currentVersion) {
        AppPlatform appPlatform = AppPlatform.from(platform)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PLATFORM_MESSAGE));
        AppVersion current = AppVersion.tryParse(currentVersion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_VERSION_MESSAGE));

        PlatformPolicy policy = currentSnapshot().policies().getOrDefault(appPlatform, PlatformPolicy.DISABLED);
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

    /** 직전에 warn으로 남긴 설정 경고 목록(테스트 확인용) */
    List<String> loggedWarnings() {
        refreshLock.lock();
        try {
            return Collections.unmodifiableList(loggedWarnings);
        } finally {
            refreshLock.unlock();
        }
    }

    // ===== 스냅샷 캐시 =====

    private Snapshot currentSnapshot() {
        Snapshot current = snapshot;
        if (current != null && clock.instant().isBefore(current.refreshAt())) {
            return current;
        }
        if (current == null) {
            // 첫 로드. 쓸 값이 없으므로 기다렸다가 먼저 잡은 스레드의 결과를 쓴다
            refreshLock.lock();
        } else if (!refreshLock.tryLock()) {
            // 다른 스레드가 갱신 중이다. 기다리지 않고 직전 값을 쓴다
            return current;
        }
        try {
            Snapshot latest = snapshot;
            if (latest != null && clock.instant().isBefore(latest.refreshAt())) {
                return latest;
            }
            Snapshot refreshed = load(latest);
            snapshot = refreshed;
            return refreshed;
        } finally {
            refreshLock.unlock();
        }
    }

    /** refreshLock 안에서만 부른다 */
    private Snapshot load(Snapshot previous) {
        Instant refreshAt = clock.instant().plus(CACHE_TTL);
        List<AppVersionSetting> rows;
        try {
            rows = settingPort.findAll();
        } catch (RuntimeException e) {
            log.error("앱 버전 설정(app_version) 조회 실패. {}초 뒤 다시 시도하고 그때까지 {} 값을 쓴다",
                    CACHE_TTL.toSeconds(), previous == null ? "꺼진 상태(NONE)" : "마지막 정상", e);
            Map<AppPlatform, PlatformPolicy> fallback = previous == null ? Map.of() : previous.policies();
            return new Snapshot(fallback, refreshAt);
        }

        List<String> warnings = new ArrayList<>();
        Map<AppPlatform, PlatformPolicy> policies = new EnumMap<>(AppPlatform.class);
        for (AppVersionSetting row : rows) {
            if (row.getPlatform() != null) {
                policies.put(row.getPlatform(), resolve(row, warnings));
            }
        }
        if (!warnings.equals(loggedWarnings)) {
            warnings.forEach(log::warn);
            loggedWarnings = List.copyOf(warnings);
        }
        return new Snapshot(Collections.unmodifiableMap(policies), refreshAt);
    }

    // ===== 설정 해석 =====

    private static PlatformPolicy resolve(AppVersionSetting row, List<String> warnings) {
        String name = row.getPlatform().name();
        String latestRaw = blankToNull(row.getLatestVersion());
        String minRaw = blankToNull(row.getMinSupportedVersion());
        String storeUrl = blankToNull(row.getStoreUrl());

        if (latestRaw == null) {
            if (minRaw != null) {
                warnings.add(String.format(
                        "app_version(%s).latest_version이 비어 있어 min_supported_version(%s)이 있어도 버전 확인은 꺼져 있다(항상 NONE)",
                        name, minRaw));
            }
            return PlatformPolicy.DISABLED;
        }

        Optional<AppVersion> latest = AppVersion.tryParse(latestRaw);
        if (latest.isEmpty()) {
            warnings.add(String.format(
                    "app_version(%s).latest_version 형식이 올바르지 않아(%s) 버전 확인을 끈다(항상 NONE)", name, latestRaw));
            return PlatformPolicy.DISABLED;
        }

        AppVersion minSupported = null;
        String minSupportedRaw = null;
        if (minRaw != null) {
            Optional<AppVersion> parsed = AppVersion.tryParse(minRaw);
            if (parsed.isEmpty()) {
                warnings.add(String.format(
                        "app_version(%s).min_supported_version 형식이 올바르지 않아(%s) 버전 확인을 끈다(항상 NONE)",
                        name, minRaw));
                return PlatformPolicy.DISABLED;
            }
            if (latest.get().isLowerThan(parsed.get())) {
                warnings.add(String.format(
                        "app_version(%s).min_supported_version(%s)이 latest_version(%s)보다 커서 강제 업데이트를 무시하고 권장만 판정한다",
                        name, minRaw, latestRaw));
            } else {
                minSupported = parsed.get();
                minSupportedRaw = minRaw;
            }
        }

        if (storeUrl == null) {
            warnings.add(String.format(
                    "app_version(%s).store_url이 비어 있어 업데이트가 필요한 버전도 NONE으로 응답한다", name));
        }
        return new PlatformPolicy(latest.get(), latestRaw, minSupported, minSupportedRaw, storeUrl);
    }

    /** 앞뒤 공백은 지우고, 비어 있거나 공백뿐이면 null로 본다(DB 제약 때문에 정상적으로는 오지 않지만 방어) */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    /**
     * 해석이 끝난 설정 묶음. policies에 없는 플랫폼은 꺼진 것으로 본다.
     *
     * @param refreshAt 이 시각부터 다시 읽는다(실패한 시도도 TTL 뒤로 미룬다)
     */
    private record Snapshot(Map<AppPlatform, PlatformPolicy> policies, Instant refreshAt) {
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
