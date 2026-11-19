package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 앱 버전 확인 설정. application.yaml의 app-version 블록과 매핑되고, 값은 환경 변수(APP_VERSION_*)로 넣는다.
 * 값이 비어 있으면 그 플랫폼의 기능이 꺼진다. 형식 검사와 경고 로그는 AppVersionService가 기동 때 한 번 한다.
 * 형식이 틀려도 기동을 실패시키지 않는다(환경 변수 오타 하나로 서버가 안 뜨는 것이 더 위험하다).
 */
@ConfigurationProperties(prefix = "app-version")
public record AppVersionProperties(
        @DefaultValue Platform ios,
        @DefaultValue Platform android
) {

    /**
     * 플랫폼별 값. 앞뒤 공백은 지우고, 비어 있거나 공백뿐이면 null로 둔다.
     *
     * @param latest       최신 버전. 비어 있으면 이 플랫폼은 항상 NONE이다
     * @param minSupported 최소 지원 버전. 이보다 낮으면 FORCE다. 비어 있으면 FORCE 없이 OPTIONAL만 나간다
     * @param storeUrl     스토어 이동 주소. 비어 있으면 업데이트가 필요해도 NONE으로 낮춘다
     */
    public record Platform(String latest, String minSupported, String storeUrl) {

        public Platform {
            latest = blankToNull(latest);
            minSupported = blankToNull(minSupported);
            storeUrl = blankToNull(storeUrl);
        }

        private static String blankToNull(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.strip();
        }
    }
}
