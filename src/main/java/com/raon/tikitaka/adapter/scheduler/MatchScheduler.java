package com.raon.tikitaka.adapter.scheduler;

import com.raon.tikitaka.application.match.in.EnsureFutureStagesUseCase;
import com.raon.tikitaka.application.match.in.OpenRoundUseCase;
import com.raon.tikitaka.application.user.in.ApplyLocationSwapUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 라운드 운영 배치 시간표. 실행 순서에 의존 관계가 있다:
 *
 * 00:10 지역 스위칭 적용 — 라운드가 자정에 갈리므로 시작 직후에 예약을 반영한다.
 *       (정각 00:00을 피한 이유: 라운드 경계의 시각 비교와 겹치는 순간을 만들지 않기 위해)
 * 05:05 미래 라운드(Stage 행) 생성 — 30일치 선생성
 * 05:10 매치·게시판 생성 — 05:05가 만든 라운드를 대상으로 하므로 반드시 뒤에 온다
 *
 * 모든 배치는 멱등이라 서버 재시작·수동 트리거(admin API)와 겹쳐 실행돼도 안전하다.
 * try/catch로 감싼 이유: 스케줄 메서드에서 예외가 새어나가도 다음 회차는 돌지만,
 * 어떤 배치가 왜 죽었는지 우리 메시지로 남기기 위해서다.
 *
 * ⚠️ 휴면 전환(DeactivateInactiveUsers)은 의도적으로 여기 없다 —
 * 로그인 브랜치 병합으로 로그인이 touch()를 호출하기 전까지는 스케줄 금지.
 *
 * (단일 인스턴스 배포 전제. 인스턴스를 늘리면 같은 배치가 서버 수만큼 돌므로
 *  그때는 ShedLock 같은 분산 잠금을 붙여야 한다 — 지금은 EC2 1대라 불필요)
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
            log.error("매치·게시판 생성 배치 실패", e);
        }
    }
}
