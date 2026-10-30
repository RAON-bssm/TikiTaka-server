package com.raon.tikitaka.adapter.territory.dto;

import java.util.List;

/** dong_ids: 1·2·3순위 순서. 빈 배열이면 희망 동을 모두 지운다 */
public record DongPreferenceRequest(List<Long> dongIds) {

    public List<Long> dongIdsOrEmpty() {
        return dongIds == null ? List.of() : dongIds;
    }
}
