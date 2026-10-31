package com.raon.tikitaka.application.territory.in;

import com.raon.tikitaka.application.territory.view.DevelopResultView;

import java.util.UUID;

public interface DevelopLandUseCase {

    /** 땅따먹기 주간에 우리 팀 땅(내 땅 제외)을 1회 개발한다 */
    DevelopResultView develop(UUID userId, Long dongCode);
}
