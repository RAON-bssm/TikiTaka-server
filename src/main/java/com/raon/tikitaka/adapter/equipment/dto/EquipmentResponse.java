package com.raon.tikitaka.adapter.equipment.dto;

import com.raon.tikitaka.application.equipment.EquippedItems;
import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
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

    public static EquipmentResponse from(EquippedItems equippedItems) {
        List<Product> products = equippedItems.products();
        Map<ColorGroup, Color> colors = equippedItems.colors();

        Map<ProductType, Product> byType = products.stream()
                .collect(Collectors.toMap(Product::getProductType, product -> product, (a, b) -> a));

        return new EquipmentResponse(
                toResponse(byType.get(ProductType.BODY), null),
                toResponse(byType.get(ProductType.ACCESSORY), null),
                toResponse(byType.get(ProductType.CLOTHING), null),
                toResponse(byType.get(ProductType.EYES), colors.get(ColorGroup.EYES)),
                toResponse(byType.get(ProductType.HAIR_FRONT), colors.get(ColorGroup.HAIR)),
                toResponse(byType.get(ProductType.HAIR_BACK), colors.get(ColorGroup.HAIR)),
                toResponse(byType.get(ProductType.MOUTH), null)
        );
    }

    private static EquipmentItemResponse toResponse(Product product, Color color) {
        return product != null ? EquipmentItemResponse.from(product, color) : null;
    }
}
