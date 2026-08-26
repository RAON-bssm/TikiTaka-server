package com.raon.tikitaka.adapter.equipment.dto;

import com.raon.tikitaka.domain.enums.ProductType;
import com.raon.tikitaka.domain.product.Product;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record EquipmentResponse(
        EquipmentItemResponse body,
        EquipmentItemResponse accessory,
        EquipmentItemResponse clothing,
        EquipmentItemResponse eyes,
        EquipmentItemResponse hairFront,
        EquipmentItemResponse hairBack,
        EquipmentItemResponse mouth
) {

    public static EquipmentResponse from(List<Product> products) {
        Map<ProductType, EquipmentItemResponse> byType = products.stream()
                .collect(Collectors.toMap(Product::getProductType, EquipmentItemResponse::from, (a, b) -> a));

        return new EquipmentResponse(
                byType.get(ProductType.BODY),
                byType.get(ProductType.ACCESSORY),
                byType.get(ProductType.CLOTHING),
                byType.get(ProductType.EYES),
                byType.get(ProductType.HAIR_FRONT),
                byType.get(ProductType.HAIR_BACK),
                byType.get(ProductType.MOUTH)
        );
    }
}
