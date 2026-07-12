package com.raon.tikitaka.adapter.product.dto;

import com.raon.tikitaka.domain.product.Product;

import java.util.List;

public record ProductListResponse(List<ProductItemResponse> product) {

    public static ProductListResponse from(List<Product> products) {
        return new ProductListResponse(products.stream().map(ProductItemResponse::from).toList());
    }
}
