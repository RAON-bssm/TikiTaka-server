package com.raon.tikitaka.application.user.out;

import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.userItem.EquippedColor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EquippedColorRepositoryPort {

    List<EquippedColor> findAllByUserId(UUID userId);

    void deleteAllByUserIdAndColorGroups(UUID userId, Collection<ColorGroup> colorGroups);

    List<EquippedColor> saveAll(List<EquippedColor> equippedColors);
}
