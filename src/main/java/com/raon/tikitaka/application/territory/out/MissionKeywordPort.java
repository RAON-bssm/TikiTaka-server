package com.raon.tikitaka.application.territory.out;

import java.util.List;

/** 기본 미션용 키워드. 매치 미션과 같은 keyword 테이블을 쓴다 */
public interface MissionKeywordPort {

    List<String> findAdjectives();

    List<String> findNouns();
}
