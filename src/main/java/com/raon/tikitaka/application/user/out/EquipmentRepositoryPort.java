package com.raon.tikitaka.application.user.out;

import com.raon.tikitaka.domain.enums.ProductType;
import com.raon.tikitaka.domain.userItem.Equipment;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EquipmentRepositoryPort {

    List<Equipment> findAllByUserId(UUID userId);

    void deleteAllByUserIdAndProductTypes(UUID userId, Collection<ProductType> productTypes);

    List<Equipment> saveAll(List<Equipment> equipmentList);
}
