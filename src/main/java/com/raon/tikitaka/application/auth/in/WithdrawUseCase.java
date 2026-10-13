package com.raon.tikitaka.application.auth.in;

import java.util.UUID;

public interface WithdrawUseCase {

    /**
     * 회원 탈퇴. 유저를 익명화(WITHDRAWN)하고 refresh token을 삭제해 재로그인을 막는다.
     * 같은 소셜 계정으로의 재가입은 막지 않는다 (신규 가입으로 처리됨).
     * Apple 유저는 authorizationCode로 Apple 연동 해지를 먼저 하고, 해지에 실패하면 탈퇴하지 않는다.
     * 다른 provider는 authorizationCode를 쓰지 않는다(null 가능).
     */
    void withdraw(UUID userId, String authorizationCode);
}
