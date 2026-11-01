package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 게시물 AI 심사 대기열 설정. application.yaml의 post-review 블록과 매핑된다.
 *
 * @param async             true면 업로드는 PENDING 저장까지만 하고 심사는 워커가 한다. false면 기존 동기 방식
 * @param concurrency       워커가 동시에 심사하는 글 수. 분당 호출 한도가 병목이라 늘려도 처리량은 늘지 않는다
 * @param maxCallsPerMinute 워커가 최근 60초 동안 Gemini를 부를 수 있는 횟수. 미션 생성이 같은 키를 쓰므로
 *                          Gemini 한도(분당 5회)보다 1회 적게 잡는다
 * @param maxAttempts       총 시도 횟수. 이만큼 실패하면 FAILED
 * @param initialBackoff    첫 실패 뒤 대기 시간. 실패할 때마다 두 배가 된다(1, 2, 4, 8분)
 * @param claimTimeout      선점 후 이 시간이 지나도 끝나지 않은 글은 다시 선점한다(서버 중단 복구)
 */
@ConfigurationProperties(prefix = "post-review")
public record PostReviewProperties(
        @DefaultValue("false") boolean async,
        @DefaultValue("1") int concurrency,
        @DefaultValue("4") int maxCallsPerMinute,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("1m") Duration initialBackoff,
        @DefaultValue("5m") Duration claimTimeout
) {

    /**
     * failedAttempts번째 실패 뒤의 대기 시간. initialBackoff * 2^(failedAttempts - 1)
     */
    public Duration backoffAfter(int failedAttempts) {
        return initialBackoff.multipliedBy(1L << Math.max(0, failedAttempts - 1));
    }
}
