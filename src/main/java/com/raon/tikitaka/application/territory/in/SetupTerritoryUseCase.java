package com.raon.tikitaka.application.territory.in;

import com.raon.tikitaka.application.territory.view.SetupResultView;

import java.util.UUID;

public interface SetupTerritoryUseCase {

    record Command(String adjective, String noun, String scoringCriteria,
                   byte[] image, String fileName, String contentType) {
    }

    /** 5~7일차 땅 세팅: 미션 + 대표 사진. 기간 안에서는 다시 해도 된다(덮어쓴다) */
    SetupResultView setup(UUID userId, Long dongCode, Command command);
}
