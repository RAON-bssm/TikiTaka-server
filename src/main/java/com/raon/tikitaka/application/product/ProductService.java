package com.raon.tikitaka.application.product;

import com.raon.tikitaka.application.inventory.out.InventoryRepositoryPort;
import com.raon.tikitaka.application.product.in.GetProductListUseCase;
import com.raon.tikitaka.application.product.in.PurchaseProductUseCase;
import com.raon.tikitaka.application.product.out.ProductRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.user.Users;
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
public class ProductService implements GetProductListUseCase, PurchaseProductUseCase {

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

    @Override
    @Transactional
    public void purchase(UUID userId, Long productId) {
        Users user = userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다."));

        Product product = productRepositoryPort.findActiveById(productId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 상품입니다."));

        user.usePoint(product.getPrice());

        inventoryRepositoryPort.save(Inventory.of(user, product));
    }
}
