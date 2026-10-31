package com.raon.tikitaka.application.territory.view;

/** 땅 세팅 결과. defensePhoto는 S3 key(볼 때는 /api/storage/view-url) */
public record SetupResultView(Long dongCode, String mission, String missionAdjective, String missionNoun,
                              String scoringCriteria, String defensePhoto, int defenseScore, String review) {
}
