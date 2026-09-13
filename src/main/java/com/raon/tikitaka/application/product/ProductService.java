package com.raon.tikitaka.application.product;

import com.raon.tikitaka.application.inventory.out.InventoryRepositoryPort;
import com.raon.tikitaka.application.product.in.GetProductListUseCase;
import com.raon.tikitaka.application.product.in.PurchaseProductUseCase;
import com.raon.tikitaka.application.product.out.ProductRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.domain.userItem.Inventory;
import com.raon.tikitaka.global.exception.AlreadyOwnedProductException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

        if (inventoryRepositoryPort.existsByUserIdAndProductId(userId, productId)) {
            throw new AlreadyOwnedProductException(productId);
        }

        user.usePoint(product.getPrice());

        try {
            inventoryRepositoryPort.save(Inventory.of(user, product));
        } catch (DataIntegrityViolationException e) {
            // 동시에 들어온 중복 구매 요청 — existsByUserIdAndProductId 체크를 둘 다 통과한 뒤
            // uk_inventory_user_product 유니크 제약에서 걸린 경우. 트랜잭션이 롤백되어 포인트는
            // 그대로 복구되므로, 원인 불명의 500 대신 동일한 409 응답으로 정리한다.
            throw new AlreadyOwnedProductException(productId);
        }
    }
}
