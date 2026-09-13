package com.raon.tikitaka.application.ranking;

/**
 * 지역 라운드 랭킹 한 건. 네이티브 쿼리의 별칭과 getter 이름을 맞춘 인터페이스 프로젝션이다.
 */
public interface LocationRankRow {

    Long getLocationId();

    String getCityName();

    String getLocationName();

    Integer getLocationRank();

    Long getLocationScore();
}
