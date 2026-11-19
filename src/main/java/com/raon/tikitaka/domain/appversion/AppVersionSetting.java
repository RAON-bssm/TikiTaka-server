package com.raon.tikitaka.domain.appversion;

import com.raon.tikitaka.domain.enums.AppPlatform;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * 플랫폼별 앱 버전 확인 설정(app_version 테이블, 플랫폼당 한 행).
 * 운영자가 DB에 직접 접속해 UPDATE하고, 서버는 읽기만 한다(관리자 API 없음). 그래서 @Immutable로 실수로 UPDATE되지 않게 한다.
 *
 * 값을 사람이 SQL로 직접 넣으므로 오타는 DB 체크 제약이 막는다. 버전은 숫자(.숫자){0,2}(각 자리 최대 6자리),
 * 스토어 주소는 http:// 또는 https://로 시작해야 한다. min이 latest보다 큰지는 제약으로 막지 않는다
 * (서비스가 경고를 남기고 강제를 무시한다). 같은 제약이 seed/app_version.sql에도 있다.
 */
@Entity
@Immutable
@Table(name = "app_version",
        check = {
                @CheckConstraint(name = "ck_app_version_latest_format",
                        constraint = "latest_version IS NULL OR latest_version ~ '^[0-9]{1,6}(\\.[0-9]{1,6}){0,2}$'"),
                @CheckConstraint(name = "ck_app_version_min_format",
                        constraint = "min_supported_version IS NULL OR min_supported_version ~ '^[0-9]{1,6}(\\.[0-9]{1,6}){0,2}$'"),
                @CheckConstraint(name = "ck_app_version_store_url",
                        constraint = "store_url IS NULL OR store_url ~ '^https?://'")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppVersionSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", length = 20)
    private AppPlatform platform;

    /** 최신 버전. null이면 이 플랫폼의 버전 확인은 꺼진다(항상 NONE) */
    @Column(name = "latest_version", length = 20)
    private String latestVersion;

    /** 최소 지원 버전. 이보다 낮으면 FORCE. null이면 강제 없음 */
    @Column(name = "min_supported_version", length = 20)
    private String minSupportedVersion;

    /** 스토어 이동 주소. null이면 업데이트가 필요해도 NONE */
    @Column(name = "store_url", length = 500)
    private String storeUrl;

    /** 마지막 수정 시각. 운영자가 UPDATE할 때 now()를 넣는다 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 테스트에서 행을 만들 때 쓴다. 서버 코드는 이 테이블에 쓰지 않는다 */
    public static AppVersionSetting of(AppPlatform platform, String latestVersion, String minSupportedVersion,
                                       String storeUrl) {
        AppVersionSetting setting = new AppVersionSetting();
        setting.platform = platform;
        setting.latestVersion = latestVersion;
        setting.minSupportedVersion = minSupportedVersion;
        setting.storeUrl = storeUrl;
        setting.updatedAt = LocalDateTime.now();
        return setting;
    }
}
