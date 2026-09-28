package com.raon.tikitaka.adapter.equipment.dto;

import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.product.Product;

public record EquipmentItemResponse(
        String productId,
        String productName,
        String productImage,
        String color
) {

    public static EquipmentItemResponse from(Product product, Color color) {
        return new EquipmentItemResponse(
                product.getProductId(),
                product.getProductName(),
                product.getProductImage(),
                color != null ? color.getColorCode() : null
        );
    }
}
