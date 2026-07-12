package com.raon.tikitaka.adapter.inventory.out;

import com.raon.tikitaka.application.inventory.out.InventoryRepositoryPort;
import com.raon.tikitaka.domain.userItem.Inventory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InventoryPersistenceAdapter implements InventoryRepositoryPort {

    private final InventoryJpaRepository inventoryJpaRepository;

    @Override
    public List<Inventory> findAllByUserIdWithProduct(UUID userId) {
        return inventoryJpaRepository.findAllByUserIdWithProduct(userId);
    }
}
