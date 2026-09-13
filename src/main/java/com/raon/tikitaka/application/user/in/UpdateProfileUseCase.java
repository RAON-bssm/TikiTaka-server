package com.raon.tikitaka.application.user.in;

import java.util.UUID;

/**
 * 프로필 수정. 닉네임 변경과 소속 동네 변경을 처리한다.
 */
public interface UpdateProfileUseCase {

    /**
     * userName/mainLocationId는 각각 null이면 해당 항목을 변경하지 않는다.
     */
    void updateProfile(UUID userId, String userName, Long mainLocationId);
}
