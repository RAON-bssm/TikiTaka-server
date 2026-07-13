package com.raon.tikitaka.adapter.equipment.dto;

import com.raon.tikitaka.domain.product.Product;

public record EquipmentItemResponse(
        Long productId,
        String productName,
        String productImage,
        String type
) {

    public static EquipmentItemResponse from(Product product) {
        return new EquipmentItemResponse(
                product.getProductId(),
                product.getProductName(),
                product.getProductImage(),
                product.getProductType().name().toLowerCase()
        );
    }
}
