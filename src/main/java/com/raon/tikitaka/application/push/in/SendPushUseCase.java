package com.raon.tikitaka.application.push.in;

import com.raon.tikitaka.application.push.PushMessage;

import java.util.Collection;
import java.util.UUID;

/**
 * 유저에게 푸시를 보낸다. 유저의 모든 기기로 간다. 토큰이 없으면 아무것도 하지 않는다.
 * 외부 HTTP 호출이므로 DB 트랜잭션 안에서 부르지 않는다(커밋 뒤 이벤트 리스너나 스케줄러에서 부른다).
 */
public interface SendPushUseCase {

    void sendToUser(UUID userId, PushMessage message);

    void sendToUsers(Collection<UUID> userIds, PushMessage message);
}
