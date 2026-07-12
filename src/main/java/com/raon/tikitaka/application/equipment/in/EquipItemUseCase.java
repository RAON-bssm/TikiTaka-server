package com.raon.tikitaka.application.equipment.in;

import java.util.List;
import java.util.UUID;

public interface EquipItemUseCase {

    void equip(UUID userId, List<Long> productIds);
}
