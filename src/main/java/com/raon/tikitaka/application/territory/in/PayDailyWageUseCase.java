package com.raon.tikitaka.application.territory.in;

/** 땅따먹기 라운드 일급. 하루에 한 번만 지급된다(같은 날 다시 돌려도 지급하지 않는다) */
public interface PayDailyWageUseCase {

    /** 05:57 매일: 땅 주인마다 가진 땅값 합계 × 비율만큼 포인트 지급 */
    void payDailyWage();
}
