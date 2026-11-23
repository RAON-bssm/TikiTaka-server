package com.raon.tikitaka.adapter.push;

import com.raon.tikitaka.application.push.PushNotifications;
import com.raon.tikitaka.application.push.in.SendPushUseCase;
import com.raon.tikitaka.application.territory.TerritoryCapturedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 땅을 뺏기면 이전 주인에게 "{도전자}에게 {동} 땅을 뺏겼습니다!!" 푸시를 보낸다.
 *
 * 커밋 뒤(AFTER_COMMIT)에만 돌아 롤백된 도전으로 알림이 나가지 않고,
 * @Async로 도전 응답과 분리돼 FCM 지연·실패가 도전 결과에 번지지 않는다(신고 메일과 같은 방식).
 * 발송 실패는 로그만 남긴다. 점령은 이미 커밋됐다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerritoryCapturedPushListener {

    private final SendPushUseCase sendPushUseCase;
    private final PushNotifications pushNotifications;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCaptured(TerritoryCapturedEvent event) {
        try {
            sendPushUseCase.sendToUser(event.previousOwnerId(),
                    pushNotifications.territoryCaptured(event.challengerName(), event.dongName(), event.dongCode()));
        } catch (Exception e) {
            log.error("땅 뺏김 알림 발송 실패. previousOwnerId={}, dongCode={}",
                    event.previousOwnerId(), event.dongCode(), e);
        }
    }
}
