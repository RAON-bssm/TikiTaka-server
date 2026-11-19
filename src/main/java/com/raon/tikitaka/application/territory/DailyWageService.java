package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.in.PayDailyWageUseCase;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryPointPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.out.TerritoryWagePort;
import com.raon.tikitaka.application.territory.policy.WagePolicy;
import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.domain.territory.TerritoryWage;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 땅따먹기 라운드 동안 매일 땅 주인에게 일급을 준다. 일급 = floor(그 유저가 가진 땅의 땅값 합계 × rate).
 * 땅값 재계산(05:50) 뒤에 돌아서 그날의 땅값을 쓴다. 주인 없는 땅은 아무에게도 지급하지 않는다.
 * 지급할 때마다 territory_wage에 이력을 남긴다(유저 조회용).
 * 사이클의 lastWagePaidOn으로 하루 한 번만 지급하고, 한 트랜잭션이라 중간에 실패하면 전부 롤백된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DailyWageService implements PayDailyWageUseCase {

    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final TerritoryQueryPort territoryQueryPort;
    private final TerritoryPointPort pointPort;
    private final TerritoryWagePort wagePort;
    private final WagePolicy wagePolicy;

    /** 한 땅 주인의 그날 집계 */
    private static final class Holding {
        private final Users owner;
        private int landCount;
        private long totalLandPrice;

        private Holding(Users owner) {
            this.owner = owner;
        }
    }

    @Override
    public void payDailyWage() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        TerritoryCycle cycle = cycleRepositoryPort.findCurrent(now)
                .filter(c -> c.roundTypeAt(now) == RoundType.BATTLE)
                .orElse(null);
        if (cycle == null || cycle.isWagePaidOn(today)) {
            return;
        }

        Map<UUID, Holding> holdings = new LinkedHashMap<>();
        for (Territory t : territoryQueryPort.findAllByCycleId(cycle.getCycleId())) {
            if (t.isOwned()) {
                Holding h = holdings.computeIfAbsent(t.getOwner().getUserId(), id -> new Holding(t.getOwner()));
                h.landCount++;
                h.totalLandPrice += t.landPrice();
            }
        }

        List<TerritoryWage> records = new ArrayList<>();
        long paid = 0;
        for (Map.Entry<UUID, Holding> e : holdings.entrySet()) {
            Holding h = e.getValue();
            int wage = wagePolicy.wage(h.totalLandPrice);
            if (wage > 0) {
                pointPort.add(e.getKey(), wage);
                records.add(TerritoryWage.of(cycle, h.owner, today, h.landCount, h.totalLandPrice,
                        wagePolicy.rate(), wage, now));
                paid += wage;
            }
        }
        wagePort.saveAll(records);
        cycle.markWagePaid(today);
        log.info("일급 지급 완료. cycleId={}, 땅 주인 {}명, 총 {}포인트", cycle.getCycleId(), records.size(), paid);
    }
}
