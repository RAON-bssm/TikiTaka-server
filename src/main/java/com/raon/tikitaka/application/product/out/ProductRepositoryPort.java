package com.raon.tikitaka.application.product.out;

import com.raon.tikitaka.domain.product.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepositoryPort {

    List<Product> findAllActiveProducts();

    Optional<Product> findActiveById(Long productId);
}
