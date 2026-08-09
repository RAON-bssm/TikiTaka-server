package com.raon.tikitaka.global.exception;

import com.raon.tikitaka.domain.enums.LoginProvider;

public class SocialLoginFailedException extends RuntimeException {

    public SocialLoginFailedException(LoginProvider provider, Throwable cause) {
        super(provider.getDescription() + " 인증에 실패했습니다.", cause);
    }
}
