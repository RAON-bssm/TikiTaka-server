package com.raon.tikitaka.application.user.out;

import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.userItem.EquippedColor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EquippedColorRepositoryPort {

    List<EquippedColor> findAllByUserId(UUID userId);

    /**
     * 여러 유저의 착용 색. 색(color)을 함께 읽고, 고른 색이 없는 유저는 결과에 없다.
     */
    List<EquippedColor> findAllByUserIds(Collection<UUID> userIds);

    void deleteAllByUserIdAndColorGroups(UUID userId, Collection<ColorGroup> colorGroups);

    List<EquippedColor> saveAll(List<EquippedColor> equippedColors);
}
