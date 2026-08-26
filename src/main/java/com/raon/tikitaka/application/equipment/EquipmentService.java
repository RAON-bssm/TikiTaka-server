package com.raon.tikitaka.application.equipment;

import com.raon.tikitaka.application.equipment.in.EquipItemUseCase;
import com.raon.tikitaka.application.equipment.in.GetEquippedItemsUseCase;
import com.raon.tikitaka.application.inventory.out.InventoryRepositoryPort;
import com.raon.tikitaka.application.user.out.EquipmentRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.enums.ProductType;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.domain.userItem.Equipment;
import com.raon.tikitaka.domain.userItem.Inventory;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EquipmentService implements EquipItemUseCase, GetEquippedItemsUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final InventoryRepositoryPort inventoryRepositoryPort;
    private final EquipmentRepositoryPort equipmentRepositoryPort;

    @Override
    public List<Product> getEquippedItems(UUID userId) {
        userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다."));

        return equipmentRepositoryPort.findAllByUserId(userId).stream()
                .map(Equipment::getProduct)
                .toList();
    }

    @Override
    public void equip(UUID userId, Map<ProductType, Long> selections) {
        Users user = userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다."));

        Map<Long, Product> ownedProducts = inventoryRepositoryPort.findAllByUserIdWithProduct(userId).stream()
                .map(Inventory::getProduct)
                .collect(Collectors.toMap(Product::getProductId, product -> product));

        List<Equipment> newEquipments = selections.entrySet().stream()
                .map(entry -> Equipment.of(user, findOwnedProductOfType(ownedProducts, entry.getKey(), entry.getValue())))
                .toList();

        equipmentRepositoryPort.deleteAllByUserId(userId);
        equipmentRepositoryPort.saveAll(newEquipments);
    }

    private Product findOwnedProductOfType(Map<Long, Product> ownedProducts, ProductType type, Long productId) {
        Product product = ownedProducts.get(productId);
        if (product == null) {
            throw new EntityNotFoundException("보유하지 않은 아이템입니다: " + productId);
        }
        if (product.getProductType() != type) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상품 타입이 일치하지 않습니다: " + productId);
        }
        return product;
    }
}
