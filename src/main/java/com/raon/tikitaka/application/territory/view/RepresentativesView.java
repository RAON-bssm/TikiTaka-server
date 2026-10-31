package com.raon.tikitaka.application.territory.view;

import com.raon.tikitaka.domain.enums.AssignmentType;

import java.util.List;
import java.util.UUID;

/**
 * 사이클의 동 대표 선발 결과. 임시 API 응답이며 프론트 확정 후 변경될 수 있다.
 * 사이클이 없으면 cycleId가 null이고, 선발 전이면 representatives가 비어 있다.
 */
public record RepresentativesView(Long cycleId, boolean representativesSelected, List<Item> representatives) {

    /** rank와 score는 선발 시점 스냅샷이다. 조회에서 빠진 유저가 있어도 다시 매기지 않는다 */
    public record Item(int rank, UUID userId, String userName, long score, Long locationId,
                       Long dongId, Long dongCode, String dongName, AssignmentType assignedBy, boolean mine) {
    }
}
