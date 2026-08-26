package com.raon.tikitaka.application.match.in;

import com.raon.tikitaka.application.match.MatchResult;

import java.util.List;

public interface GetMatchResultsUseCase {

    /**
     * 직전에 종료된 라운드의 경기 결과 목록.
     * 승패는 저장된 값이 아니라 조회 시점에 두 팀의 라운드 점수를 비교해 계산한다.
     * 아직 끝난 라운드가 없으면 빈 목록.
     */
    List<MatchResult> getMatchResults();
}
