package com.raon.tikitaka.application.inventory;

import com.raon.tikitaka.application.inventory.in.GetInventoryUseCase;
import com.raon.tikitaka.application.inventory.out.InventoryRepositoryPort;
import com.raon.tikitaka.application.user.out.EquipmentRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.userItem.Equipment;
import com.raon.tikitaka.domain.userItem.Inventory;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService implements GetInventoryUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final InventoryRepositoryPort inventoryRepositoryPort;
    private final EquipmentRepositoryPort equipmentRepositoryPort;

    @Override
    public UserInventory getInventory(UUID userId) {
        userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다."));

        List<Inventory> inventoryList = inventoryRepositoryPort.findAllByUserIdWithProduct(userId);
        List<Equipment> equipmentList = equipmentRepositoryPort.findAllByUserId(userId);

        return new UserInventory(inventoryList, equipmentList);
    }
}
