package com.raon.tikitaka.application.equipment.in;

import com.raon.tikitaka.application.equipment.EquippedItems;

import java.util.UUID;

public interface GetEquippedItemsUseCase {

    EquippedItems getEquippedItems(UUID userId);
}
