package com.raon.tikitaka.adapter.match.dto;

import com.raon.tikitaka.application.match.MatchResult;

import java.time.LocalDateTime;

/**
 * 경기 결과 응답 한 건. 필드는 API 명세서를 따른다.
 * winTeam과 lostTeam이 둘 다 null이면 무승부거나 부전승이며 matchType으로 구분한다.
 * 팀 이름은 "부산광역시 북구"처럼 도시명까지 합친 전체 지역명이다.
 * matchDate는 경기가 속한 라운드의 시작 시각이다(라운드 내 모든 경기가 동일한 값을 가진다).
 */
public record MatchResultResponse(Long matchId, String mission, String winTeam, String lostTeam, String matchType,
                                   LocalDateTime matchDate) {

    public static MatchResultResponse from(MatchResult result) {
        return new MatchResultResponse(result.matchId(), result.mission(),
                result.winTeam(), result.lostTeam(), result.matchType(), result.matchDate());
    }
}
