package com.raon.tikitaka.application.territory.view;

/**
 * 땅 개발 결과. nextCost는 다음 개발비(바뀐 땅값 × costRate), remainingToday는 오늘 남은 내 개발 횟수
 * (하루 제한이 없으면 -1). teamGain은 우리 팀 땅값에 보탠 값(실패면 0)이다.
 */
public record DevelopResultView(Long dongCode, boolean success, int cost, double probability, int teamGain,
                                int landPrice, int nextCost, int remainingToday, int myPoint) {

    /** remainingToday 값: 개인 하루 개발 제한이 없음 */
    public static final int UNLIMITED = -1;
}
