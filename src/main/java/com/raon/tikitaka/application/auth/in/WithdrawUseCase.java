package com.raon.tikitaka.application.auth.in;

import java.util.UUID;

public interface WithdrawUseCase {

    /**
     * 회원 탈퇴. 유저를 익명화(WITHDRAWN)하고 refresh token을 삭제해 재로그인을 막는다.
     * 같은 소셜 계정으로의 재가입은 막지 않는다 (신규 가입으로 처리됨).
     */
    void withdraw(UUID userId);
}
