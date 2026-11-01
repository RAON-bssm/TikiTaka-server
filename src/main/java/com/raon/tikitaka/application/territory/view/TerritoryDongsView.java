package com.raon.tikitaka.application.territory.view;

import com.raon.tikitaka.domain.enums.RoundType;

import java.util.List;
import java.util.UUID;

/**
 * 현재 사이클의 동 정보 목록. 앱은 dongCode로 패키지의 GeoJSON과 연결해 지도에 칠한다.
 * blocked = 내가 차단한 유저의 땅. 이때 ownerName은 비운다(ownerId·ownerLocationId는 팀 색과 신고에 필요해서 남긴다).
 */
public record TerritoryDongsView(Long cycleId, RoundType roundType, List<Item> territories) {

    public record Item(Long territoryId, Long dongId, Long dongCode, String dongName, Long locationId,
                       UUID ownerId, String ownerName, Long ownerLocationId,
                       int defenseScore, int landPrice, boolean mine, boolean challengeable,
                       boolean blocked) {
    }
}
