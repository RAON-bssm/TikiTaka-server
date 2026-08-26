package com.raon.tikitaka.application.match;

/**
 * 경기 결과 한 건. 승패는 저장된 값이 아니라 조회 시점에 계산한 값이다.
 * winTeam과 lostTeam이 둘 다 null이면 무승부거나 부전승 매치이며 matchType으로 구분한다.
 */
public record MatchResult(Long matchId, String mission, String winTeam, String lostTeam, String matchType) {
}
