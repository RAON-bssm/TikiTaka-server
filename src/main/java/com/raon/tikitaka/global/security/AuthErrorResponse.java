package com.raon.tikitaka.global.security;

import com.raon.tikitaka.global.security.jwt.TokenErrorCode;

/**
 * 인증/인가 실패 응답의 data 부분. 사람이 읽는 사유는 공통 래퍼의 message에,
 * 클라이언트가 분기에 쓰는 식별자는 여기 code에 담는다.
 */
public record AuthErrorResponse(String code) {

    public static final String ACCESS_DENIED = "ACCESS_DENIED";

    public static AuthErrorResponse of(TokenErrorCode code) {
        return new AuthErrorResponse(code.name());
    }

    public static AuthErrorResponse of(String code) {
        return new AuthErrorResponse(code);
    }
}
