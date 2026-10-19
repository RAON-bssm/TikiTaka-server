package com.raon.tikitaka.application.block;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 차단 목록 한 건. 설정 화면의 "차단 목록 / 해제"에 쓴다.
 */
public record BlockedUser(UUID userId, String userName, LocalDateTime blockedAt) {
}
