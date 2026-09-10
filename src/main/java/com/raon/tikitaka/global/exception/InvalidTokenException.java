package com.raon.tikitaka.global.exception;

import com.raon.tikitaka.global.security.jwt.TokenErrorCode;
import lombok.Getter;

@Getter
public class InvalidTokenException extends RuntimeException {

    private final TokenErrorCode code;

    public InvalidTokenException(TokenErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 사유를 특정하지 않는 호출부용. 재발급으로 풀리지 않는 실패로 간주한다.
     */
    public InvalidTokenException(String message) {
        this(TokenErrorCode.TOKEN_INVALID, message);
    }
}
