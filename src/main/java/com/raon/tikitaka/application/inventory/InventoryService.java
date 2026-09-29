package com.raon.tikitaka.application.inventory;

import com.raon.tikitaka.application.inventory.in.GetInventoryUseCase;
import com.raon.tikitaka.application.inventory.out.InventoryRepositoryPort;
import com.raon.tikitaka.application.user.out.EquipmentRepositoryPort;
import com.raon.tikitaka.application.user.out.EquippedColorRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.userItem.Equipment;
import com.raon.tikitaka.domain.userItem.EquippedColor;
import com.raon.tikitaka.domain.userItem.Inventory;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService implements GetInventoryUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final InventoryRepositoryPort inventoryRepositoryPort;
    private final EquipmentRepositoryPort equipmentRepositoryPort;
    private final EquippedColorRepositoryPort equippedColorRepositoryPort;

    @Override
    public UserInventory getInventory(UUID userId) {
        userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다."));

        List<Inventory> inventoryList = inventoryRepositoryPort.findAllByUserIdWithProduct(userId);
        List<Equipment> equipmentList = equipmentRepositoryPort.findAllByUserId(userId);
        Map<ColorGroup, Color> colors = equippedColorRepositoryPort.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(EquippedColor::getColorGroup, EquippedColor::getColor));

        return new UserInventory(inventoryList, equipmentList, colors);
    }
}
