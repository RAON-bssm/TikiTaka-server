package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.TerritoryWage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TerritoryWagePort {

    void saveAll(List<TerritoryWage> wages);

    /** 그 유저의 일급 이력 최신순 limit건(모든 사이클) */
    List<TerritoryWage> findRecentByUser(UUID userId, int limit);

    /** 그 유저의 한 사이클 일급 이력 최신순 limit건 */
    List<TerritoryWage> findRecentByUserAndCycle(UUID userId, Long cycleId, int limit);

    /** 그날 일급을 받은 유저 id(중복 없음). 일급 알림 대상 */
    List<UUID> findUserIdsPaidOn(LocalDate paidOn);
}
