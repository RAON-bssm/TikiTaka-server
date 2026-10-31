package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.in.DevelopLandUseCase;
import com.raon.tikitaka.application.territory.out.*;
import com.raon.tikitaka.application.territory.policy.DevelopmentPolicy;
import com.raon.tikitaka.application.territory.view.DevelopResultView;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.territory.LandDevelopment;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.TerritoryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 땅 개발 1회. 외부 호출이 없어 전체를 한 트랜잭션으로 처리한다(컨트롤러가 포인트 키로 감싼다).
 * 개발비는 매번 지금 땅값 × 180%로 계산하므로, 성공해 땅값이 오르면 다음 개발비도 그만큼 오른다.
 */
@Service
@RequiredArgsConstructor
public class LandDevelopmentService implements DevelopLandUseCase {

    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final TerritoryQueryPort territoryQueryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final TerritoryPointPort pointPort;
    private final LandDevelopmentPort developmentPort;
    private final RandomPort randomPort;
    private final DevelopmentPolicy developmentPolicy;
    private final TerritoryProperties properties;

    @Override
    @Transactional
    public DevelopResultView develop(UUID userId, Long dongCode) {
        LocalDateTime now = LocalDateTime.now();
        TerritoryCycle cycle = cycleRepositoryPort.findCurrent(now)
                .filter(c -> c.roundTypeAt(now) == RoundType.BATTLE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "땅따먹기 주간이 아닙니다."));
        Long territoryId = territoryQueryPort.findIdByCycleIdAndDongCode(cycle.getCycleId(), dongCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이 동의 땅이 없습니다."));
        // 같은 땅의 동시 개발·점령과 겹치지 않게 잠근다(하루 성공 횟수 검사도 정확해진다)
        Territory t = territoryQueryPort.findByIdForUpdate(territoryId).orElseThrow();
        Users user = userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."));

        if (!t.isDevelopableBy(userId, user.getMainLocation().getLocationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "우리 팀 땅만 개발할 수 있어요. 내 땅은 팀원이 개발해요.");
        }
        TerritoryProperties.Development d = properties.development();
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        long usedToday = developmentPort.countByUserSince(userId, today);
        if (usedToday >= d.dailyLimitPerUser()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "오늘은 더 개발할 수 없어요. 하루 " + d.dailyLimitPerUser() + "번까지예요.");
        }
        if (developmentPort.countSuccessByTerritorySince(territoryId, today) >= d.dailyGainLimitPerDong()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이 땅은 오늘 더 개발할 수 없어요.");
        }

        int cost = developmentPolicy.cost(t.landPrice());
        if (!pointPort.deduct(userId, cost)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "포인트가 부족해요.");
        }
        double roll = randomPort.nextDouble();
        boolean success = developmentPolicy.isSuccess(roll);
        int gain = success ? developmentPolicy.gain() : 0;
        if (success) {
            t.develop(gain);
        }
        developmentPort.save(LandDevelopment.record(t, user, cost, developmentPolicy.probability(), roll, success, gain, now));

        return new DevelopResultView(dongCode, success, cost, developmentPolicy.probability(), gain, t.landPrice(),
                developmentPolicy.cost(t.landPrice()), (int) (d.dailyLimitPerUser() - usedToday - 1),
                pointPort.getPoint(userId));
    }
}
