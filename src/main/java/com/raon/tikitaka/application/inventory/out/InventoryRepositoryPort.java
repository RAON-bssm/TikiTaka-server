package com.raon.tikitaka.application.inventory.out;

import com.raon.tikitaka.domain.userItem.Inventory;

import java.util.List;
import java.util.UUID;

public interface InventoryRepositoryPort {

    List<Inventory> findAllByUserIdWithProduct(UUID userId);

    boolean existsByUserIdAndProductId(UUID userId, Long productId);

    Inventory save(Inventory inventory);
}
