package com.raon.tikitaka.application.territory.view;

import com.raon.tikitaka.domain.enums.RoundType;

import java.time.LocalDateTime;

/** 현재 사이클. 진행 중인 사이클이 없으면 cycleId가 null이고 roundType은 NONE이다 */
public record CurrentCycleView(Long cycleId, RoundType roundType,
                               LocalDateTime pointStartedAt, LocalDateTime rankingFreezeAt,
                               LocalDateTime battleStartedAt, LocalDateTime battleEndedAt,
                               boolean representativesSelected, Long myLocationId) {
}
