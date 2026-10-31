package com.raon.tikitaka.application.territory.view;

import java.util.UUID;

/**
 * 동 상세. defensePhoto는 S3 key라 기존 /api/storage/view-url로 연다.
 * pendingMission은 내 땅일 때만 채운다.
 */
public record TerritoryDetailView(Long territoryId, Long dongCode, String dongName, Long locationId,
                                  UUID ownerId, String ownerName, Long ownerLocationId,
                                  String mission, String scoringCriteria, String pendingMission,
                                  String defensePhoto, int defenseScore, int landPrice, int toll,
                                  int developmentCost, double developmentProbability,
                                  boolean mine, boolean challengeable) {
}
