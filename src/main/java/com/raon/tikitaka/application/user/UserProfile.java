package com.raon.tikitaka.application.user;

import com.raon.tikitaka.application.ranking.UserRankRow;
import com.raon.tikitaka.domain.user.Users;

/**
 * 프로필 조회 결과. myRanking은 이번 라운드에 게시물을 하나도 안 올렸거나
 * 진행 중인 라운드가 없으면 null이다.
 */
public record UserProfile(Users user, UserRankRow myRanking) {
}
