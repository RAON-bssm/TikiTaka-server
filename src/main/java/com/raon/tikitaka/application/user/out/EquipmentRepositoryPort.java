package com.raon.tikitaka.application.user.out;

import com.raon.tikitaka.domain.userItem.Equipment;

import java.util.List;
import java.util.UUID;

public interface EquipmentRepositoryPort {

    List<Equipment> findAllByUserId(UUID userId);

    void deleteAllByUserId(UUID userId);

    List<Equipment> saveAll(List<Equipment> equipmentList);
}
