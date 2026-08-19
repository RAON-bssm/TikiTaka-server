package com.raon.tikitaka.application.match;

import com.raon.tikitaka.application.match.in.OpenRoundUseCase;
import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.global.config.SeasonProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 매치·게시판 자동 생성의 진입점.
 * "매치가 아직 없는 임박한 라운드"를 찾아 라운드마다 OpenRoundProcessor를 호출한다.
 *
 * 트랜잭션은 여기가 아니라 OpenRoundProcessor.open()에 걸려 있다 — 라운드 하나의 생성은
 * 전부 성공 또는 전부 롤백이어야 하고(반쪽 라운드 고착 방지), 한 라운드의 실패가
 * 다른 라운드 처리까지 되돌리면 안 되기 때문에 라운드별로 별도 트랜잭션을 쓴다.
 * (같은 클래스 내부 호출은 스프링 프록시를 타지 않으므로 별도 빈으로 분리했다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService implements OpenRoundUseCase {

    private final StageRepositoryPort stageRepositoryPort;
    private final SeasonProperties seasonProperties;
    private final OpenRoundProcessor openRoundProcessor;

    @Override
    public void execute() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusDays(seasonProperties.matchOpenAheadDays());

        List<Stage> targets = stageRepositoryPort.findUpcomingWithoutMatch(now, threshold);
        if (targets.isEmpty()) {
            return;     // 할 일 없음 — 매일 돌아도 안전
        }

        for (Stage stage : targets) {
            try {
                openRoundProcessor.open(stage.getStageId());
            } catch (Exception e) {
                // 이 라운드의 트랜잭션은 통째로 롤백됐고(매치 0건 유지), 다음 실행에서 재시도된다
                log.error("라운드 개장 실패 (stage {}, season {} round {}): {}",
                        stage.getStageId(), stage.getSeason(), stage.getRound(), e.getMessage(), e);
            }
        }
    }
}
