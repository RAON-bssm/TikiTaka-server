package com.raon.tikitaka.application.push;

import java.util.List;

/**
 * 발송 결과. invalidTokens는 FCM이 "더 이상 쓸 수 없는 토큰"이라고 답한 것만 담는다(앱 삭제, 토큰 만료 등).
 * 서비스는 이 목록을 그대로 지우기만 하고, 어떤 에러를 무효로 볼지는 어댑터가 정한다.
 */
public record PushSendResult(int successCount, List<String> invalidTokens) {

    public PushSendResult {
        invalidTokens = invalidTokens == null ? List.of() : List.copyOf(invalidTokens);
    }

    public static PushSendResult empty() {
        return new PushSendResult(0, List.of());
    }
}
