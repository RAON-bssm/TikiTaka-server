package com.raon.tikitaka.application.territory.view;

/** 미션 변경 요청 결과. mission은 아직 지금 미션이고, pending*이 다시 찍을 때 적용될 미션이다 */
public record MissionChangeView(Long dongCode, String mission, String pendingAdjective, String pendingNoun,
                                String pendingCriteria) {
}
