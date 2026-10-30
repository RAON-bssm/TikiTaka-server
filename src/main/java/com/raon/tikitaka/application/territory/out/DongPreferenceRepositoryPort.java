package com.raon.tikitaka.application.territory.out;

import java.util.Collection;
import java.util.UUID;

public interface DongPreferenceRepositoryPort {

    /** 본진이 바뀌는 유저들의 희망 동을 지운다 */
    void deleteAllByUserIds(Collection<UUID> userIds);
}
