package com.raon.tikitaka.application.ranking;

import java.util.List;

/**
 * 개인 랭킹 조회 결과. 상위 목록과 호출자 본인의 순위를 담는다.
 * myRanking은 본인이 이번 라운드에 게시물을 하나도 안 올렸으면 null이다.
 */
public record UserRankingResult(List<UserRankRow> ranking, UserRankRow myRanking) {
}
