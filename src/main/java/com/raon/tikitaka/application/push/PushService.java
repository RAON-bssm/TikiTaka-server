package com.raon.tikitaka.application.push;

import com.raon.tikitaka.application.push.in.CleanUpPushTokenUseCase;
import com.raon.tikitaka.application.push.in.PushTokenUseCase;
import com.raon.tikitaka.application.push.in.SendPushUseCase;
import com.raon.tikitaka.application.push.out.PushSenderPort;
import com.raon.tikitaka.application.push.out.PushTokenPort;
import com.raon.tikitaka.domain.enums.AppPlatform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * FCM 토큰 관리와 발송.
 *
 * 발송(sendToUsers)에는 트랜잭션을 걸지 않는다. FCM 호출이 느려도 DB 커넥션을 붙잡지 않게 하려는 것이다.
 * 무효 토큰 삭제는 리포지토리 메서드의 트랜잭션으로 따로 커밋된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService implements PushTokenUseCase, SendPushUseCase, CleanUpPushTokenUseCase {

    static final int MAX_TOKEN_LENGTH = 512;
    /** IN 절 하나에 넣을 유저 수. PostgreSQL 바인드 파라미터 한도(32767)보다 충분히 작게 */
    static final int USER_ID_CHUNK = 1000;
    /** 이 기간 동안 앱이 토큰을 다시 등록하지 않으면 쓰지 않는 기기로 본다(Firebase 권장 약 2개월) */
    static final int STALE_DAYS = 60;

    static final String EMPTY_TOKEN_MESSAGE = "푸시 토큰이 비어 있습니다.";
    static final String TOO_LONG_TOKEN_MESSAGE = "푸시 토큰 형식이 올바르지 않습니다.";
    static final String INVALID_PLATFORM_MESSAGE = "지원하지 않는 플랫폼입니다.";

    private final PushTokenPort pushTokenPort;
    private final PushSenderPort pushSenderPort;

    @Override
    @Transactional
    public void register(UUID userId, String token, String platform) {
        String trimmed = validToken(token);
        AppPlatform appPlatform = AppPlatform.from(platform)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PLATFORM_MESSAGE));
        pushTokenPort.upsert(userId, trimmed, appPlatform, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void unregister(UUID userId, String token) {
        pushTokenPort.delete(userId, validToken(token));
    }

    @Override
    public void sendToUser(UUID userId, PushMessage message) {
        sendToUsers(List.of(userId), message);
    }

    @Override
    public void sendToUsers(Collection<UUID> userIds, PushMessage message) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<UUID> distinct = new ArrayList<>(new LinkedHashSet<>(userIds));
        for (int from = 0; from < distinct.size(); from += USER_ID_CHUNK) {
            List<UUID> chunk = distinct.subList(from, Math.min(from + USER_ID_CHUNK, distinct.size()));
            List<String> tokens = pushTokenPort.findTokensByUserIds(chunk);
            if (tokens.isEmpty()) {
                continue;
            }
            PushSendResult result = pushSenderPort.send(tokens, message);
            if (!result.invalidTokens().isEmpty()) {
                pushTokenPort.deleteAllByTokens(result.invalidTokens());
                log.info("무효 푸시 토큰 {}개 삭제", result.invalidTokens().size());
            }
        }
    }

    @Override
    @Transactional
    public void cleanUpStaleTokens() {
        int deleted = pushTokenPort.deleteNotUpdatedSince(LocalDateTime.now().minusDays(STALE_DAYS));
        log.info("오래된 푸시 토큰 정리 완료. {}개 삭제", deleted);
    }

    private static String validToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, EMPTY_TOKEN_MESSAGE);
        }
        String trimmed = token.trim();
        if (trimmed.length() > MAX_TOKEN_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, TOO_LONG_TOKEN_MESSAGE);
        }
        return trimmed;
    }
}
