package com.raon.tikitaka.application.appversion.policy;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 앱 버전(major.minor.patch). 스프링에 의존하지 않는 값 객체다.
 * 형식은 숫자(.숫자){0,2}이고 각 자리는 최대 6자리다. 빠진 자리는 0으로 보므로 1.3과 1.3.0은 같다.
 * 비교는 자리별 숫자 비교라 1.10.0이 1.9.0보다 크다(문자열 비교를 쓰지 않는다).
 */
public record AppVersion(int major, int minor, int patch) implements Comparable<AppVersion> {

    // \d는 UNICODE_CHARACTER_CLASS 없이 ASCII 숫자만 잡는다. 각 자리 6자리 제한이라 int 범위를 넘지 않는다
    private static final Pattern FORMAT = Pattern.compile("^\\d{1,6}(\\.\\d{1,6}){0,2}$");

    public AppVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("버전 자리는 음수일 수 없습니다");
        }
    }

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    /** 형식이 맞으면 버전, 아니면 빈 값. null과 빈 문자열도 빈 값이다 */
    public static Optional<AppVersion> tryParse(String value) {
        if (!isValid(value)) {
            return Optional.empty();
        }
        String[] parts = value.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        return Optional.of(new AppVersion(major, minor, patch));
    }

    /** 형식이 틀리면 IllegalArgumentException */
    public static AppVersion parse(String value) {
        return tryParse(value).orElseThrow(() -> new IllegalArgumentException("앱 버전 형식이 올바르지 않습니다: " + value));
    }

    public boolean isLowerThan(AppVersion other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(AppVersion other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
