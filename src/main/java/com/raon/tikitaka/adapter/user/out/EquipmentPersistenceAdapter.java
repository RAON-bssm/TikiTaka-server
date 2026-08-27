package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.application.user.out.EquipmentRepositoryPort;
import com.raon.tikitaka.domain.enums.ProductType;
import com.raon.tikitaka.domain.userItem.Equipment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EquipmentPersistenceAdapter implements EquipmentRepositoryPort {

    private final EquipmentJpaRepository equipmentJpaRepository;

    @Override
    public List<Equipment> findAllByUserId(UUID userId) {
        return equipmentJpaRepository.findAllByUserIdWithProduct(userId);
    }

    @Override
    public void deleteAllByUserIdAndProductTypes(UUID userId, Collection<ProductType> productTypes) {
        equipmentJpaRepository.deleteAllByUser_UserIdAndProduct_ProductTypeIn(userId, productTypes);
    }

    @Override
    public List<Equipment> saveAll(List<Equipment> equipmentList) {
        return equipmentJpaRepository.saveAll(equipmentList);
    }
}
