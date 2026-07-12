package com.raon.tikitaka.application.equipment;

import com.raon.tikitaka.application.equipment.in.EquipItemUseCase;
import com.raon.tikitaka.application.inventory.out.InventoryRepositoryPort;
import com.raon.tikitaka.application.user.out.EquipmentRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.user.User;
import com.raon.tikitaka.domain.userItem.Equipment;
import com.raon.tikitaka.domain.userItem.Inventory;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EquipmentService implements EquipItemUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final InventoryRepositoryPort inventoryRepositoryPort;
    private final EquipmentRepositoryPort equipmentRepositoryPort;

    @Override
    public void equip(UUID userId, List<Long> productIds) {
        User user = userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다."));

        Map<Long, Product> ownedProducts = inventoryRepositoryPort.findAllByUserIdWithProduct(userId).stream()
                .map(Inventory::getProduct)
                .collect(Collectors.toMap(Product::getProductId, product -> product));

        List<Equipment> newEquipments = productIds.stream()
                .map(productId -> Equipment.of(user, findOwnedProduct(ownedProducts, productId)))
                .toList();

        equipmentRepositoryPort.deleteAllByUserId(userId);
        equipmentRepositoryPort.saveAll(newEquipments);
    }

    private Product findOwnedProduct(Map<Long, Product> ownedProducts, Long productId) {
        Product product = ownedProducts.get(productId);
        if (product == null) {
            throw new EntityNotFoundException("보유하지 않은 아이템입니다: " + productId);
        }
        return product;
    }
}
