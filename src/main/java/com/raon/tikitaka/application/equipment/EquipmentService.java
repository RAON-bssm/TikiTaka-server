package com.raon.tikitaka.application.equipment;

import com.raon.tikitaka.application.color.out.ColorRepositoryPort;
import com.raon.tikitaka.application.equipment.in.EquipItemUseCase;
import com.raon.tikitaka.application.equipment.in.GetEquippedItemsUseCase;
import com.raon.tikitaka.application.inventory.out.InventoryRepositoryPort;
import com.raon.tikitaka.application.user.out.EquipmentRepositoryPort;
import com.raon.tikitaka.application.user.out.EquippedColorRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.enums.ProductType;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.domain.userItem.Equipment;
import com.raon.tikitaka.domain.userItem.EquippedColor;
import com.raon.tikitaka.domain.userItem.Inventory;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EquipmentService implements EquipItemUseCase, GetEquippedItemsUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final InventoryRepositoryPort inventoryRepositoryPort;
    private final EquipmentRepositoryPort equipmentRepositoryPort;
    private final EquippedColorRepositoryPort equippedColorRepositoryPort;
    private final ColorRepositoryPort colorRepositoryPort;

    @Override
    public EquippedItems getEquippedItems(UUID userId) {
        userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다."));

        List<Product> products = equipmentRepositoryPort.findAllByUserId(userId).stream()
                .map(Equipment::getProduct)
                .toList();

        Map<ColorGroup, Color> colors = equippedColorRepositoryPort.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(EquippedColor::getColorGroup, EquippedColor::getColor));

        return new EquippedItems(products, colors);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, EquippedItems> getEquippedItemsByUserIds(Collection<UUID> userIds) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (UUID userId : userIds) {
            if (userId != null) {
                ids.add(userId);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }

        // Equipment.user는 LAZY지만 프록시의 id 접근은 초기화하지 않으므로 유저 조회 쿼리가 따로 나가지 않는다
        Map<UUID, List<Product>> productsByUser = new HashMap<>();
        for (Equipment equipment : equipmentRepositoryPort.findAllByUserIds(ids)) {
            productsByUser.computeIfAbsent(equipment.getUser().getUserId(), id -> new ArrayList<>())
                    .add(equipment.getProduct());
        }

        Map<UUID, Map<ColorGroup, Color>> colorsByUser = new HashMap<>();
        for (EquippedColor equippedColor : equippedColorRepositoryPort.findAllByUserIds(ids)) {
            colorsByUser.computeIfAbsent(equippedColor.getUser().getUserId(), id -> new EnumMap<>(ColorGroup.class))
                    .put(equippedColor.getColorGroup(), equippedColor.getColor());
        }

        Map<UUID, EquippedItems> result = new HashMap<>();
        for (UUID id : ids) {
            result.put(id, new EquippedItems(productsByUser.getOrDefault(id, List.of()),
                    colorsByUser.getOrDefault(id, Map.of())));
        }
        return result;
    }

    @Override
    public void equip(UUID userId, Map<ProductType, String> productSelections, Map<ColorGroup, String> colorSelections) {
        if (productSelections.isEmpty() && colorSelections.isEmpty()) {
            return;
        }

        Users user = userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다."));

        if (!productSelections.isEmpty()) {
            equipProducts(user, productSelections);
        }
        if (!colorSelections.isEmpty()) {
            equipColors(user, colorSelections);
        }
    }

    private void equipProducts(Users user, Map<ProductType, String> selections) {
        Map<String, Product> ownedProducts = inventoryRepositoryPort.findAllByUserIdWithProduct(user.getUserId()).stream()
                .map(Inventory::getProduct)
                .collect(Collectors.toMap(Product::getProductId, product -> product));

        List<Equipment> newEquipments = selections.entrySet().stream()
                .map(entry -> Equipment.of(user, findOwnedProductOfType(ownedProducts, entry.getKey(), entry.getValue())))
                .toList();

        equipmentRepositoryPort.deleteAllByUserIdAndProductTypes(user.getUserId(), selections.keySet());
        equipmentRepositoryPort.saveAll(newEquipments);
    }

    private void equipColors(Users user, Map<ColorGroup, String> selections) {
        List<EquippedColor> newEquippedColors = selections.entrySet().stream()
                .map(entry -> EquippedColor.of(user, entry.getKey(), findColorOfGroup(entry.getKey(), entry.getValue())))
                .toList();

        equippedColorRepositoryPort.deleteAllByUserIdAndColorGroups(user.getUserId(), selections.keySet());
        equippedColorRepositoryPort.saveAll(newEquippedColors);
    }

    private Product findOwnedProductOfType(Map<String, Product> ownedProducts, ProductType type, String productId) {
        Product product = ownedProducts.get(productId);
        if (product == null) {
            throw new EntityNotFoundException("보유하지 않은 아이템입니다: " + productId);
        }
        if (product.getProductType() != type) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상품 타입이 일치하지 않습니다: " + productId);
        }
        return product;
    }

    private Color findColorOfGroup(ColorGroup group, String colorCode) {
        return colorRepositoryPort.findByColorGroupAndColorCode(group, colorCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 색상입니다: " + colorCode));
    }
}
