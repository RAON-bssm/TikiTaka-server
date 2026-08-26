package com.raon.tikitaka.application.match;

import com.raon.tikitaka.application.match.in.EnsureFutureStagesUseCase;
import com.raon.tikitaka.application.match.in.GetCurrentStageUseCase;
import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.global.config.SeasonProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StageService implements EnsureFutureStagesUseCase, GetCurrentStageUseCase {

    private final StageRepositoryPort stageRepositoryPort;
    private final SeasonProperties seasonProperties;

    /**
     * 미래 라운드를 prefetchDays 일수만큼 미리 생성한다.
     * 이미 채워져 있으면 아무것도 하지 않고 며칠 중단됐다 재실행되면 밀린 라운드를 한 번에 채운다.
     * 라운드 경계는 직전 라운드의 ended_at에서 이어지므로 시드의 자정 기준이 계속 유지된다.
     */
    @Override
    public void execute() {
        Optional<Stage> latest = stageRepositoryPort.findLatestByEndedAt();
        if (latest.isEmpty()) {
            // 예외를 던지면 시드 전까지 매일 에러 로그가 쌓이므로 경고만 남기고 종료한다
            log.warn("stage 테이블이 비어 있습니다. 첫 라운드(season 1, round 1)를 시드해야 라운드 자동 생성이 동작합니다.");
            return;
        }

        Stage last = latest.get();
        LocalDateTime horizon = LocalDateTime.now().plusDays(seasonProperties.prefetchDays());

        int created = 0;
        while (last.getEndedAt().isBefore(horizon)) {
            int season = last.getSeason();
            int round = last.getRound() + 1;
            if (round > seasonProperties.roundsPerSeason()) {
                season += 1;
                round = 1;
            }
            Stage next = Stage.create(season, round,
                    last.getEndedAt(),
                    last.getEndedAt().plusDays(seasonProperties.roundDays()));
            last = stageRepositoryPort.save(next);
            created++;
        }

        if (created > 0) {
            log.info("미래 라운드 {}개 생성 완료 (마지막: season {}, round {}, ~{})",
                    created, last.getSeason(), last.getRound(), last.getEndedAt());
        }
    }

    /**
     * 시즌 라운드 조회 API에서 쓰는 현재 진행 중인 라운드.
     * 시드 전이거나 라운드 생성이 오래 멈춘 비정상 상황에서만 404가 난다.
     */
    @Override
    public Stage getCurrentStage() {
        Optional<Stage> current = stageRepositoryPort.findCurrent(LocalDateTime.now());
        if (current.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "진행 중인 라운드가 없습니다.");
        }
        return current.get();
    }
}
