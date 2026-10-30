package com.raon.tikitaka.application.territory.in;

import com.raon.tikitaka.domain.territory.DongPreference;

import java.util.List;
import java.util.UUID;

public interface ManageDongPreferenceUseCase {

    List<DongPreference> getPreferences(UUID userId);

    /** dongIds는 1·2·3순위 순서. 0~3개. 기존 희망 동을 통째로 바꾼다 */
    List<DongPreference> replacePreferences(UUID userId, List<Long> dongIds);
}
