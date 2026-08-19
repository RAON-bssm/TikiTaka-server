package com.raon.tikitaka.adapter.ranking.dto;

import com.raon.tikitaka.application.ranking.UserRankRow;

import java.util.UUID;

/**
 * 개인 랭킹 응답 한 건. 필드는 API 명세서를 따른다.
 */
public record UserRankingResponse(UUID userId, String userName, Integer userRank, Long userScore) {

    public static UserRankingResponse from(UserRankRow row) {
        return new UserRankingResponse(row.getUserId(), row.getUserName(),
                row.getUserRank(), row.getUserScore());
    }
}
