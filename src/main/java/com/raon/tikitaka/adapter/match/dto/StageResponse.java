package com.raon.tikitaka.adapter.match.dto;

import com.raon.tikitaka.domain.match.Stage;

import java.time.LocalDateTime;

/**
 * 시즌 및 라운드 조회 응답. 필드는 API 명세서를 따른다.
 * 전역 SNAKE_CASE 설정 때문에 stageId는 stage_id로 직렬화된다.
 */
public record StageResponse(Long stageId, Integer season, Integer round, LocalDateTime startedAt) {

    public static StageResponse from(Stage stage) {
        return new StageResponse(stage.getStageId(), stage.getSeason(), stage.getRound(), stage.getStartedAt());
    }
}
