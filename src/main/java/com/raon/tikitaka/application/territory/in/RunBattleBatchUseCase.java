package com.raon.tikitaka.application.territory.in;

/** 땅따먹기 주간 배치. 모두 멱등하다(다시 돌려도 결과가 같다) */
public interface RunBattleBatchUseCase {

    /** 05:45 땅따먹기 1일차: 미션이 없는 땅에 기본 미션 + 기준 방어 점수 */
    void applyDefaultMissions();

    /** 05:50 매일: 인기도로 기본 땅값 재계산 */
    void recalculateLandPrices();

    /** 05:55 매일: 방치된 땅의 방어 점수 감쇠 */
    void decayDefense();
}
