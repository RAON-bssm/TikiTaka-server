package com.raon.tikitaka.adapter.user.dto;

/**
 * 프로필 수정 요청. 세 필드 다 null이면 해당 항목은 변경하지 않는다.
 */
public record UpdateProfileRequest(String userName, Long mainLocationId, Long subLocationId) {
}
