package com.raon.tikitaka.application.territory.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 대표 후보 조회. 기존 개인 랭킹(user_ranking)을 읽기만 한다 */
public interface RepresentativeCandidatePort {

    record CandidateRow(UUID userId, long score) {
    }

    /**
     * 그 라운드의 개인 랭킹에서 본진이 locationId인 ACTIVE 유저 상위 limit명.
     * 점수 내림차순, 같으면 그 점수에 먼저 도달한(마지막 갱신이 이른) 사람이 앞선다.
     */
    List<CandidateRow> findTopCandidates(Long stageId, Long locationId, int limit);

    /** 그 라운드에서 writtenBefore 전에 올라왔는데 아직 AI 심사가 끝나지 않은(PENDING) 활성 글 수 */
    long countPendingReviews(Long stageId, LocalDateTime writtenBefore);
}
