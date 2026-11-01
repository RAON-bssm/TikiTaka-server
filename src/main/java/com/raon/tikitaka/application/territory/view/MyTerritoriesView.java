package com.raon.tikitaka.application.territory.view;

import com.raon.tikitaka.domain.enums.RoundType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 땅 목록. defensePhoto는 S3 key(볼 때는 /api/storage/view-url).
 * ownerLastActiveAt은 마지막으로 대표 사진을 찍은(또는 땅을 얻은) 시각이다. 오래되면 방어 점수가 깎인다.
 */
public record MyTerritoriesView(Long cycleId, RoundType roundType, int maxOwned, List<Item> territories) {

    public record Item(Long territoryId, Long dongCode, String dongName, Long locationId,
                       String mission, String missionAdjective, String missionNoun, String scoringCriteria,
                       String pendingAdjective, String pendingNoun, String pendingCriteria,
                       String defensePhoto, int defenseScore, int landPrice, int developmentValue,
                       LocalDateTime ownerLastActiveAt) {
    }
}
