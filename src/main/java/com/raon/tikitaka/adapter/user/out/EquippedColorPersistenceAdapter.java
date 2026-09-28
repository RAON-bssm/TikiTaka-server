package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.application.user.out.EquippedColorRepositoryPort;
import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.userItem.EquippedColor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EquippedColorPersistenceAdapter implements EquippedColorRepositoryPort {

    private final EquippedColorJpaRepository equippedColorJpaRepository;

    @Override
    public List<EquippedColor> findAllByUserId(UUID userId) {
        return equippedColorJpaRepository.findAllByUserIdWithColor(userId);
    }

    @Override
    public void deleteAllByUserIdAndColorGroups(UUID userId, Collection<ColorGroup> colorGroups) {
        equippedColorJpaRepository.deleteAllByUser_UserIdAndColorGroupIn(userId, colorGroups);
    }

    @Override
    public List<EquippedColor> saveAll(List<EquippedColor> equippedColors) {
        return equippedColorJpaRepository.saveAll(equippedColors);
    }
}
