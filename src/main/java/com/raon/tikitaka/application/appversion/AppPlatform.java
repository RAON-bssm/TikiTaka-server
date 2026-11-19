package com.raon.tikitaka.application.appversion;

import java.util.Locale;
import java.util.Optional;

/** 버전 확인을 지원하는 앱 플랫폼. 요청 값은 대소문자를 무시한다 */
public enum AppPlatform {
    IOS,
    ANDROID;

    public static Optional<AppPlatform> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "ios" -> Optional.of(IOS);
            case "android" -> Optional.of(ANDROID);
            default -> Optional.empty();
        };
    }
}
