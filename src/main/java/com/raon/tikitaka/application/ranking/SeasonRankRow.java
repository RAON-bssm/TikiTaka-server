package com.raon.tikitaka.application.ranking;

/**
 * 시즌 확정 쿼리의 결과 한 건. 동네의 시즌 합산 점수와 최종 등수를 담는다.
 * 네이티브 쿼리 결과는 인터페이스 프로젝션으로 받는데
 * 쿼리의 별칭과 getter 이름을 맞춰두면 Spring이 구현체를 만들어 값을 채워준다.
 */
public interface SeasonRankRow {

    Long getLocationId();

    Long getFinalScore();

    Integer getFinalRank();
}
