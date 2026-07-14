package com.raon.tikitaka.application.product.in;

import com.raon.tikitaka.domain.product.Product;

import java.util.List;
import java.util.UUID;

public interface GetProductListUseCase {

    List<Product> getProducts(UUID userId);
}
