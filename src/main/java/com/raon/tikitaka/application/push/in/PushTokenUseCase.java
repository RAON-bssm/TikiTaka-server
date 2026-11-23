package com.raon.tikitaka.application.push.in;

import java.util.UUID;

/** 앱이 FCM 토큰을 등록·해제한다 */
public interface PushTokenUseCase {

    /** 앱 실행, 로그인, 토큰 갱신 때마다 부른다. 같은 토큰을 여러 번 보내도 행은 하나다(upsert) */
    void register(UUID userId, String token, String platform);

    /** 로그아웃 직전에 부른다. 내 토큰만 지운다 */
    void unregister(UUID userId, String token);
}
