package com.raon.tikitaka.application.user.in;

import java.util.UUID;

/**
 * 프로필 수정. 닉네임 변경과 동네 변경을 함께 처리하되, 동네 변경은
 * ManageLocationChangeUseCase(다음 라운드 시작 시 적용될 예약)에 위임한다.
 * 소속 동네를 그 자리에서 바로 바꾸는 기능은 없다 — 라운드 중간 팀 이탈을
 * 막기 위해 변경은 항상 다음 라운드부터 적용된다.
 */
public interface UpdateProfileUseCase {

    /**
     * userName/mainLocationId는 각각 null이면 해당 항목을 변경하지 않는다.
     * mainLocationId를 주는 건 "다음 라운드에 이 동네로 옮겨 달라"는 예약 요청이라
     * 응답이 성공이어도 소속은 다음 라운드가 시작돼야 실제로 바뀐다.
     */
    void updateProfile(UUID userId, String userName, Long mainLocationId);
}
