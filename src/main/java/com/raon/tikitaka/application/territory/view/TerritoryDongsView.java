package com.raon.tikitaka.application.territory.view;

import com.raon.tikitaka.domain.enums.RoundType;

import java.util.List;
import java.util.UUID;

/** 현재 사이클의 동 정보 목록. 앱은 dongCode로 패키지의 GeoJSON과 연결해 지도에 칠한다 */
public record TerritoryDongsView(Long cycleId, RoundType roundType, List<Item> territories) {

    public record Item(Long territoryId, Long dongId, Long dongCode, String dongName, Long locationId,
                       UUID ownerId, String ownerName, Long ownerLocationId,
                       int defenseScore, int landPrice, boolean mine, boolean challengeable) {
    }
}
