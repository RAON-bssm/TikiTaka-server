package com.raon.tikitaka.application.territory.in;

import com.raon.tikitaka.application.territory.view.SetupResultView;

import java.util.UUID;

public interface RetakeDefensePhotoUseCase {

    record Command(byte[] image, String fileName, String contentType) {
    }

    /**
     * 땅따먹기 라운드에 내 땅의 대표 사진을 다시 찍는다. 대기 미션이 있으면 그 미션으로 채점하고 적용한다.
     * 새 점수가 방어 점수를 대신한다(기존보다 낮아도 바뀐다, 결정 #17).
     */
    SetupResultView retake(UUID userId, Long dongCode, Command command);
}
