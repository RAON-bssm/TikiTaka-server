package com.raon.tikitaka.application.user.out;

import com.raon.tikitaka.domain.enums.ProductType;
import com.raon.tikitaka.domain.userItem.Equipment;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EquipmentRepositoryPort {

    List<Equipment> findAllByUserId(UUID userId);

    /**
     * 여러 유저의 착용 상품. 상품(product)을 함께 읽고, 착용한 상품이 없는 유저는 결과에 없다.
     */
    List<Equipment> findAllByUserIds(Collection<UUID> userIds);

    void deleteAllByUserIdAndProductTypes(UUID userId, Collection<ProductType> productTypes);

    List<Equipment> saveAll(List<Equipment> equipmentList);
}
