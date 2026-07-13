package com.raon.tikitaka.adapter.equipment.dto;

import com.raon.tikitaka.domain.product.Product;

import java.util.List;

public record EquipmentListResponse(List<EquipmentItemResponse> product) {

    public static EquipmentListResponse from(List<Product> products) {
        return new EquipmentListResponse(products.stream().map(EquipmentItemResponse::from).toList());
    }
}
