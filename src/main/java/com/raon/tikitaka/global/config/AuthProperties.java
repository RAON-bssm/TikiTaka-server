package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 관련 운영 설정. application.yaml의 auth 블록과 매핑된다.
 *
 * @param signupEnabled false면 회원가입 API를 잠근다. 점검이나 일시 중단에 쓴다.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(boolean signupEnabled) {
}
