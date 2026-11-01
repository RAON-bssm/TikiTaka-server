package com.raon.tikitaka.application.territory.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 땅 개발 이력. 내가 차단한 유저의 기록은 userName을 비우고 blocked=true로 준다 */
public record DevelopmentHistoryView(Long dongCode, String dongName, List<Item> developments) {

    public record Item(Long developmentId, UUID userId, String userName, boolean success, int cost,
                       int valueAdded, LocalDateTime createdAt, boolean mine, boolean blocked) {
    }
}
