package com.raon.tikitaka.global.security.jwt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인증 실패 사유. 클라이언트가 재발급을 시도할지 로그아웃할지 판단하는 근거가 된다.
 * 응답 바디의 data.code로 내려간다.
 */
@Getter
@RequiredArgsConstructor
public enum TokenErrorCode {

    /** access token 만료. 클라이언트는 refresh를 시도해야 한다 */
    TOKEN_EXPIRED("토큰이 만료되었습니다."),

    /** 서명 불일치, 형식 오류, 토큰 타입 불일치. 재발급으로 해결되지 않으므로 재로그인이 필요하다 */
    TOKEN_INVALID("유효하지 않은 토큰입니다."),

    /** Authorization 헤더가 없거나 Bearer 형식이 아니다 */
    TOKEN_MISSING("인증이 필요합니다.");

    private final String message;
}
