package com.raon.tikitaka.adapter.territory.dto;

import java.util.List;

/** dong_codes: 행정동 코드(앱 열거형 값), 1·2·3순위 순서. 빈 배열이면 희망 동을 모두 지운다 */
public record DongPreferenceRequest(List<Long> dongCodes) {

    public List<Long> dongCodesOrEmpty() {
        return dongCodes == null ? List.of() : dongCodes;
    }
}
