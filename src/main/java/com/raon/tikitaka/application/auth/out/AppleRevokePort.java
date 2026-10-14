package com.raon.tikitaka.application.auth.out;

public interface AppleRevokePort {

    /**
     * 앱과 Apple ID의 연동을 해지한다(Sign in with Apple 토큰 revoke).
     * authorization code를 Apple 토큰으로 교환한 뒤 revoke하며,
     * code가 appleUserId 본인의 것이 아니면 revoke하지 않고 실패한다.
     */
    void revoke(String authorizationCode, String appleUserId);
}
