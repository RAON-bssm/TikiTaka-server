package com.raon.tikitaka.application.territory.view;

import java.util.UUID;

/**
 * 동 상세. defensePhoto는 S3 key라 기존 /api/storage/view-url로 연다.
 * mission은 표시용(형용사 + 공백 + 명사). pending*(대기 중인 미션 변경)은 내 땅일 때만 채운다.
 * blocked = 내가 차단한 유저의 땅. 이때 ownerName과 defensePhoto는 비운다. 미션은 도전에 필요해서 남긴다.
 */
public record TerritoryDetailView(Long territoryId, Long dongCode, String dongName, Long locationId,
                                  UUID ownerId, String ownerName, Long ownerLocationId,
                                  String mission, String missionAdjective, String missionNoun, String scoringCriteria,
                                  String pendingAdjective, String pendingNoun, String pendingCriteria,
                                  String defensePhoto, int defenseScore, int landPrice, int toll,
                                  int developmentCost, double developmentProbability,
                                  boolean mine, boolean challengeable, boolean blocked) {
}
