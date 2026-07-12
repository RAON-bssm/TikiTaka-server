package com.raon.tikitaka.adapter.inventory.dto;

import com.raon.tikitaka.application.inventory.UserInventory;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.userItem.Equipment;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record InventoryResponse(List<InventoryItemResponse> product) {

    public static InventoryResponse from(UserInventory userInventory) {
        Set<Long> equippedProductIds = userInventory.equipmentList().stream()
                .map(Equipment::getProduct)
                .map(Product::getProductId)
                .collect(Collectors.toSet());

        List<InventoryItemResponse> product = userInventory.inventoryList().stream()
                .map(inventory -> InventoryItemResponse.of(
                        inventory.getProduct(),
                        equippedProductIds.contains(inventory.getProduct().getProductId())
                ))
                .toList();

        return new InventoryResponse(product);
    }
}
