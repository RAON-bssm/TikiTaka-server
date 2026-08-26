package com.raon.tikitaka.application.ranking;

import java.util.UUID;

/**
 * 개인 라운드 랭킹 한 건. 네이티브 쿼리의 별칭과 getter 이름을 맞춘 인터페이스 프로젝션이다.
 */
public interface UserRankRow {

    UUID getUserId();

    String getUserName();

    Integer getUserRank();

    Long getUserScore();
}
