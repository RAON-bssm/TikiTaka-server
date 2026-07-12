package com.raon.tikitaka.application.product;

import com.raon.tikitaka.application.inventory.out.InventoryRepositoryPort;
import com.raon.tikitaka.application.product.in.GetProductListUseCase;
import com.raon.tikitaka.application.product.out.ProductRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.userItem.Inventory;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService implements GetProductListUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final InventoryRepositoryPort inventoryRepositoryPort;

    @Override
    public List<Product> getProducts(UUID userId) {
        userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다."));

        Set<Long> ownedProductIds = inventoryRepositoryPort.findAllByUserIdWithProduct(userId).stream()
                .map(Inventory::getProduct)
                .map(Product::getProductId)
                .collect(Collectors.toSet());

        return productRepositoryPort.findAllActiveProducts().stream()
                .filter(product -> !ownedProductIds.contains(product.getProductId()))
                .toList();
    }
}
