package com.raon.tikitaka.application.territory.in;

import com.raon.tikitaka.application.territory.view.TeamRankingView;

import java.util.UUID;

public interface TeamRankingUseCase {

    /** cycleId가 null이면 진행 중인 사이클. 대표 선발 전이거나 사이클이 없으면 가장 최근에 확정된 사이클 */
    TeamRankingView getRanking(UUID userId, Long cycleId);

    /** 땅따먹기 라운드가 끝난 사이클의 랭킹을 스냅샷으로 저장하고 사이클을 닫는다 */
    void finalizeEndedCycles();
}
