package com.raon.tikitaka.application.territory.view;

/**
 * 땅 개발 결과. nextCost는 다음 개발비(바뀐 땅값 × 180%), remainingToday는 오늘 남은 내 개발 횟수.
 * teamGain은 우리 팀 땅값에 보탠 값(실패면 0)이다.
 */
public record DevelopResultView(Long dongCode, boolean success, int cost, double probability, int teamGain,
                                int landPrice, int nextCost, int remainingToday, int myPoint) {
}
