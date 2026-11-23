package com.raon.tikitaka.application.territory;

import java.util.UUID;

/**
 * 주인 있는 땅이 점령됐다(CAPTURED). 이전 주인에게 알림을 보내는 데 쓴다.
 * 리스너는 커밋 뒤 별도 스레드에서 돌아 영속성 컨텍스트가 없으므로, 필요한 값을 트랜잭션 안에서 풀어 담는다.
 * 주인 없는 땅을 처음 차지한 경우(CLAIMED)에는 발행하지 않는다.
 */
public record TerritoryCapturedEvent(
        UUID previousOwnerId,
        String challengerName,
        Long dongCode,
        String dongName
) {
}
