package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.LandDevelopment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LandDevelopmentPort {

    void save(LandDevelopment development);

    /** from 이후 그 유저의 개발 시도 수(성공·실패 모두) */
    long countByUserSince(UUID userId, LocalDateTime from);

    /** from 이후 그 땅의 개발 성공 수 */
    long countSuccessByTerritorySince(Long territoryId, LocalDateTime from);

    /** 그 땅의 개발 이력 최신순 limit건. 개발한 유저를 함께 읽는다 */
    List<LandDevelopment> findRecentByTerritory(Long territoryId, int limit);
}
