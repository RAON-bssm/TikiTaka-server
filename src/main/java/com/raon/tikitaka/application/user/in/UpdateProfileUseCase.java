package com.raon.tikitaka.application.user.in;

import java.util.UUID;

/**
 * 프로필 수정. 닉네임 변경과 동네 변경을 함께 처리하되, 동네 변경은
 * ManageLocationSwapUseCase(서브 동네 설정 + 다음 라운드 스위칭 예약)에 위임한다.
 * 메인 동네를 그 자리에서 바로 바꾸는 기능은 없다 — 라운드 중간 팀 이탈을
 * 막기 위해 스위칭은 항상 다음 라운드부터 적용되는 기존 규칙을 그대로 따른다.
 */
public interface UpdateProfileUseCase {

    /**
     * userName/mainLocationId/subLocationId는 각각 null이면 해당 항목을 변경하지 않는다.
     * mainLocationId를 주는 건 "다음 라운드에 메인이 이 동네가 되게 해달라"는 뜻이라
     * subLocationId로 이미 설정됐거나 이번 요청에서 함께 설정하는 동네와 일치해야 한다.
     */
    void updateProfile(UUID userId, String userName, Long mainLocationId, Long subLocationId);
}
