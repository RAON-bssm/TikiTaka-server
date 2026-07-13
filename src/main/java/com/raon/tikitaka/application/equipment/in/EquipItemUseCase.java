package com.raon.tikitaka.application.equipment.in;

import com.raon.tikitaka.domain.enums.ProductType;

import java.util.Map;
import java.util.UUID;

public interface EquipItemUseCase {

    void equip(UUID userId, Map<ProductType, Long> selections);
}
