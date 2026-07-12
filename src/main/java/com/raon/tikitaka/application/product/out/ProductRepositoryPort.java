package com.raon.tikitaka.application.product.out;

import com.raon.tikitaka.domain.product.Product;

import java.util.List;

public interface ProductRepositoryPort {

    List<Product> findAllActiveProducts();
}
