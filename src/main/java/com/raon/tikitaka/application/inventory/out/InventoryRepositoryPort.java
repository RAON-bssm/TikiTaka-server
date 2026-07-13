package com.raon.tikitaka.application.inventory.out;

import com.raon.tikitaka.domain.userItem.Inventory;

import java.util.List;
import java.util.UUID;

public interface InventoryRepositoryPort {

    List<Inventory> findAllByUserIdWithProduct(UUID userId);

    Inventory save(Inventory inventory);
}
