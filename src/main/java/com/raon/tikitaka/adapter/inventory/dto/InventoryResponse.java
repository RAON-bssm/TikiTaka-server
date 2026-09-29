package com.raon.tikitaka.adapter.inventory.dto;

import com.raon.tikitaka.application.inventory.UserInventory;
import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.userItem.Equipment;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record InventoryResponse(List<InventoryItemResponse> product, String hairColor, String eyesColor) {

    public static InventoryResponse from(UserInventory userInventory) {
        Set<String> equippedProductIds = userInventory.equipmentList().stream()
                .map(Equipment::getProduct)
                .map(Product::getProductId)
                .collect(Collectors.toSet());

        List<InventoryItemResponse> product = userInventory.inventoryList().stream()
                .map(inventory -> InventoryItemResponse.of(
                        inventory.getProduct(),
                        equippedProductIds.contains(inventory.getProduct().getProductId())
                ))
                .toList();

        Map<ColorGroup, Color> colors = userInventory.colors();
        Color hairColor = colors.get(ColorGroup.HAIR);
        Color eyesColor = colors.get(ColorGroup.EYES);

        return new InventoryResponse(
                product,
                hairColor != null ? hairColor.getColorCode() : null,
                eyesColor != null ? eyesColor.getColorCode() : null
        );
    }
}
