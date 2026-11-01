package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.LandDevelopment;

import java.time.LocalDateTime;
import java.util.UUID;

public interface LandDevelopmentPort {

    void save(LandDevelopment development);

    /** from 이후 그 유저의 개발 시도 수(성공·실패 모두) */
    long countByUserSince(UUID userId, LocalDateTime from);

    /** from 이후 그 땅의 개발 성공 수 */
    long countSuccessByTerritorySince(Long territoryId, LocalDateTime from);
}
