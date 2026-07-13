package com.raon.tikitaka.adapter.product.out;

import com.raon.tikitaka.application.product.out.ProductRepositoryPort;
import com.raon.tikitaka.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public List<Product> findAllActiveProducts() {
        return productJpaRepository.findAllActiveProducts();
    }

    @Override
    public Optional<Product> findActiveById(Long productId) {
        return productJpaRepository.findActiveById(productId);
    }
}
