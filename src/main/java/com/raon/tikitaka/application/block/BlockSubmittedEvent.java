package com.raon.tikitaka.application.block;

import java.time.LocalDateTime;

/**
 * 새 차단이 만들어졌을 때의 이벤트. 이미 차단 중이던 중복 요청에서는 발행하지 않는다.
 * 리스너는 커밋 이후 별도 스레드에서 돌아 영속성 컨텍스트가 없으므로
 * 메일에 필요한 값을 트랜잭션 안에서 전부 문자열로 풀어 담는다.
 */
public record BlockSubmittedEvent(
        Long blockId,
        String blockerName,
        String blockerId,
        String blockedName,
        String blockedId,
        LocalDateTime createdAt
) {
}
