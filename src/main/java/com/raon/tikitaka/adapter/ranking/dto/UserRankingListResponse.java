package com.raon.tikitaka.adapter.ranking.dto;

import com.raon.tikitaka.application.ranking.UserRankRow;
import com.raon.tikitaka.application.ranking.UserRankingResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * myRanking은 명세서에 없는 추가 필드로 100등 밖이어도 본인 순위를 내려준다.
 * 미참여면 null이고 전역 non_null 설정 때문에 응답에서 생략된다.
 */
public record UserRankingListResponse(LocalDateTime updatedAt,
                                      List<UserRankingResponse> userRanking,
                                      UserRankingResponse myRanking) {

    public static UserRankingListResponse from(UserRankingResult result) {
        List<UserRankingResponse> ranking = new ArrayList<>();
        for (UserRankRow row : result.ranking()) {
            ranking.add(UserRankingResponse.from(row));
        }
        UserRankingResponse myRanking = null;
        if (result.myRanking() != null) {
            myRanking = UserRankingResponse.from(result.myRanking());
        }
        return new UserRankingListResponse(LocalDateTime.now(), ranking, myRanking);
    }
}
