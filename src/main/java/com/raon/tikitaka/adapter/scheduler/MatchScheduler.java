package com.raon.tikitaka.adapter.scheduler;

import com.raon.tikitaka.application.match.in.EnsureFutureStagesUseCase;
import com.raon.tikitaka.application.match.in.OpenRoundUseCase;
import com.raon.tikitaka.application.user.in.ApplyLocationSwapUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 라운드 운영 배치 시간표. 실행 순서에 의존 관계가 있다.
 * 00:10 지역 스위칭 적용. 라운드가 자정에 갈리므로 시작 직후에 예약을 반영한다.
 * 05:05 미래 라운드 생성. 05:10 매치와 게시판 생성은 이 결과를 쓰므로 반드시 뒤에 온다.
 * 모든 배치는 멱등이라 서버 재시작이나 수동 실행과 겹쳐도 안전하다.
 * try로 감싼 이유는 어떤 배치가 왜 죽었는지 로그로 남기기 위해서다.
 * 단일 인스턴스 전제라 분산 잠금은 걸지 않았다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchScheduler {

    private final ApplyLocationSwapUseCase applyLocationSwapUseCase;
    private final EnsureFutureStagesUseCase ensureFutureStagesUseCase;
    private final OpenRoundUseCase openRoundUseCase;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void applyLocationSwaps() {
        try {
            applyLocationSwapUseCase.execute();
        } catch (Exception e) {
            log.error("지역 스위칭 적용 배치 실패", e);
        }
    }

    @Scheduled(cron = "0 5 5 * * *", zone = "Asia/Seoul")
    public void ensureFutureStages() {
        try {
            ensureFutureStagesUseCase.execute();
        } catch (Exception e) {
            log.error("미래 라운드 생성 배치 실패", e);
        }
    }

    @Scheduled(cron = "0 10 5 * * *", zone = "Asia/Seoul")
    public void openRounds() {
        try {
            openRoundUseCase.execute();
        } catch (Exception e) {
            log.error("매치, 게시판 생성 배치 실패", e);
        }
    }
}
