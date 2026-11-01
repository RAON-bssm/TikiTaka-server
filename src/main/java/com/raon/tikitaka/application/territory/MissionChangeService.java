package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.in.ChangeMissionUseCase;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.policy.MissionTextPolicy;
import com.raon.tikitaka.application.territory.view.MissionChangeView;
import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 미션 변경 요청. AI를 부르지 않는다(채점 기준의 부적절함은 다시 찍을 때 채점 호출에서 함께 판정).
 * 재촬영과 겹치지 않게 땅 행을 잠그고 저장한다.
 */
@Service
@RequiredArgsConstructor
public class MissionChangeService implements ChangeMissionUseCase {

    private final MissionTextPolicy missionTextPolicy;
    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final TerritoryQueryPort territoryQueryPort;

    @Override
    @Transactional
    public MissionChangeView requestChange(UUID userId, Long dongCode, String adjective, String noun,
                                           String scoringCriteria) {
        String criteria = scoringCriteria == null || scoringCriteria.isBlank() ? null : scoringCriteria.trim();
        missionTextPolicy.validate(adjective, noun, criteria);

        LocalDateTime now = LocalDateTime.now();
        TerritoryCycle cycle = cycleRepositoryPort.findCurrent(now)
                .filter(cy -> cy.roundTypeAt(now) == RoundType.BATTLE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "미션 변경은 땅따먹기 라운드에만 할 수 있어요. 세팅 기간에는 땅 세팅으로 바꿔 주세요."));
        // 엔티티를 먼저 읽으면 잠금 조회가 1차 캐시의 옛 값을 돌려주므로 id만 찾고 잠금 조회한다
        Long territoryId = territoryQueryPort.findIdByCycleIdAndDongCode(cycle.getCycleId(), dongCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이 동의 땅이 없습니다."));
        Territory t = territoryQueryPort.findByIdForUpdate(territoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이 동의 땅이 없습니다."));
        if (!t.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내 땅의 미션만 바꿀 수 있어요.");
        }
        t.requestMissionChange(adjective, noun, criteria);
        return new MissionChangeView(dongCode, t.getMission(), t.getPendingAdjective(), t.getPendingNoun(),
                t.getPendingCriteria());
    }
}
