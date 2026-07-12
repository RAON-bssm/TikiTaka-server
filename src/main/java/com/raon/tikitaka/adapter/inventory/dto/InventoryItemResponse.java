package com.raon.tikitaka.adapter.inventory.dto;

import com.raon.tikitaka.domain.product.Product;

public record InventoryItemResponse(
        Long productId,
        String productName,
        String productImage,
        String type,
        boolean isActive
) {

    public static InventoryItemResponse of(Product product, boolean isActive) {
        return new InventoryItemResponse(
                product.getProductId(),
                product.getProductName(),
                product.getProductImage(),
                product.getProductType().name().toLowerCase(),
                isActive
        );
    }
}
