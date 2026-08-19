package com.raon.tikitaka.application.ranking;

/**
 * 시즌 확정 쿼리의 결과 한 줄 — 동네의 시즌 합산 점수와 최종 등수.
 * 클래스가 아니라 인터페이스인 이유: 네이티브 쿼리 결과는 Spring Data의
 * "인터페이스 프로젝션"으로 받는다 — 쿼리의 별칭(locationId 등)과 getter 이름을
 * 맞춰두면 Spring이 구현체를 만들어 컬럼 값을 채워준다.
 */
public interface SeasonRankRow {

    Long getLocationId();

    Long getFinalScore();

    Integer getFinalRank();
}
