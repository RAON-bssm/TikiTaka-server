package com.raon.tikitaka.adapter.product.dto;

import com.raon.tikitaka.domain.product.Product;

public record ProductItemResponse(
        Long productId,
        String productName,
        Integer price,
        String productImage,
        String productType
) {

    public static ProductItemResponse from(Product product) {
        return new ProductItemResponse(
                product.getProductId(),
                product.getProductName(),
                product.getPrice(),
                product.getProductImage(),
                product.getProductType().name().toLowerCase()
        );
    }
}
