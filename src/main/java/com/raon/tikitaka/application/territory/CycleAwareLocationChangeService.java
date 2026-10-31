package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.application.territory.out.DongPreferenceRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.user.LocationChangeService;
import com.raon.tikitaka.application.user.in.ApplyLocationChangeUseCase;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [데코레이터] ApplyLocationChangeUseCase의 기본 구현(LocationChangeService)을 감싼다. @Primary라서
 * MatchScheduler는 코드 변경 없이 이 클래스를 부른다.
 *
 * 본진 변경은 땅따먹기 사이클 시작(POINT 라운드)에만 적용한다. 사이클 도중에 본진이 바뀌면
 * 대표의 팀과 땅의 팀, 촬영 지역이 어긋나기 때문이다.
 * - BATTLE 라운드: 적용하지 않고 swap_applied만 true로 표시한다. 이 플래그의 의미는
 *   "이번 라운드의 본진 변경 처리 끝(적용 또는 의도적 보류)"이다. 읽는 곳은 LocationChangeService뿐이다.
 * - POINT 라운드: 본진이 바뀔 유저의 희망 동을 지운 뒤 기본 구현에 위임한다.
 * - 사이클 밖 라운드: 기존 동작 그대로 위임한다.
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class CycleAwareLocationChangeService implements ApplyLocationChangeUseCase {

    private final LocationChangeService delegate;
    private final StageRepositoryPort stageRepositoryPort;
    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final DongPreferenceRepositoryPort dongPreferenceRepositoryPort;

    @Override
    @Transactional
    public void execute() {
        Stage stage = stageRepositoryPort.findCurrent(LocalDateTime.now()).orElse(null);
        if (stage == null || stage.isLocationChangeApplied()) {
            delegate.execute();     // 라운드 없음·이미 적용 처리는 기존 로직(로그 포함)에 맡긴다
            return;
        }

        RoundType roundType = cycleRepositoryPort.findByStageId(stage.getStageId())
                .map(cycle -> cycle.roundTypeOf(stage.getStageId()))
                .orElse(RoundType.NONE);

        if (roundType == RoundType.BATTLE) {
            stage.markLocationChangeApplied();
            log.info("땅따먹기 라운드라 본진 변경 적용을 다음 사이클로 미룹니다. (season {}, round {})",
                    stage.getSeason(), stage.getRound());
            return;
        }

        if (roundType == RoundType.POINT) {
            List<Users> movers = userRepositoryPort.findAllWithPendingLocationChange();
            dongPreferenceRepositoryPort.deleteAllByUserIds(movers.stream().map(Users::getUserId).toList());
        }
        delegate.execute();
    }
}
