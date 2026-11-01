package com.raon.tikitaka.adapter.territory.dto;

import java.util.List;

/** dong_codes: 행정동 코드(앱 열거형 값), 1·2·3순위 순서. 빈 배열이면 희망 동을 모두 지운다 */
public record DongPreferenceRequest(List<Long> dongCodes) {

    /** 필드가 빠진 요청도 "전부 삭제"로 다루려고 null을 빈 목록으로 바꾼다 */
    public List<Long> dongCodesOrEmpty() {
        return dongCodes == null ? List.of() : dongCodes;
    }
}
