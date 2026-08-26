package com.raon.tikitaka.application.match.in;

import com.raon.tikitaka.domain.match.Stage;

public interface GetCurrentStageUseCase {

    /**
     * 지금 진행 중인 라운드를 돌려준다. 없으면 404.
     */
    Stage getCurrentStage();
}
