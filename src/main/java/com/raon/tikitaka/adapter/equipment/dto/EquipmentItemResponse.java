package com.raon.tikitaka.adapter.equipment.dto;

import com.raon.tikitaka.domain.product.Product;

public record EquipmentItemResponse(
        Long productId,
        String productName,
        String productImage
) {

    public static EquipmentItemResponse from(Product product) {
        return new EquipmentItemResponse(
                product.getProductId(),
                product.getProductName(),
                product.getProductImage()
        );
    }
}
