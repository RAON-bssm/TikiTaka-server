package com.raon.tikitaka.application.territory.in;

import com.raon.tikitaka.application.territory.view.MissionChangeView;

import java.util.UUID;

public interface ChangeMissionUseCase {

    /**
     * 땅따먹기 라운드에 내 땅의 미션을 바꾼다. 바로 적용하지 않고 대기 미션으로 저장하고,
     * 대표 사진을 다시 찍을 때 적용된다. 다시 요청하면 대기 미션을 덮어쓴다.
     */
    MissionChangeView requestChange(UUID userId, Long dongCode, String adjective, String noun, String scoringCriteria);
}
