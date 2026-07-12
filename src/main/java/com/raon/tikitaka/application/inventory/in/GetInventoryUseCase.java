package com.raon.tikitaka.application.inventory.in;

import com.raon.tikitaka.application.inventory.UserInventory;

import java.util.UUID;

public interface GetInventoryUseCase {

    UserInventory getInventory(UUID userId);
}
