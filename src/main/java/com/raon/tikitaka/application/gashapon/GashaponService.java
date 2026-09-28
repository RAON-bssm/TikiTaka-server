package com.raon.tikitaka.application.gashapon;

import com.raon.tikitaka.application.gashapon.in.DrawGashaponUseCase;
import com.raon.tikitaka.application.inventory.out.InventoryRepositoryPort;
import com.raon.tikitaka.application.product.out.ProductRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.enums.ProductType;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.domain.userItem.Inventory;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional
public class GashaponService implements DrawGashaponUseCase {

    // 가샤폰 상품 ID → 1회 구매당 뽑는 횟수.
    // 종류가 늘어나면 여기에 한 줄만 추가하면 된다.
    private static final Map<String, Integer> DRAW_COUNT_BY_PRODUCT_ID = Map.of(
            "gashapon-1set", 1,
            "gashapon-5set", 5
    );

    private final UserRepositoryPort userRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final InventoryRepositoryPort inventoryRepositoryPort;

    @Override
    public List<GashaponDrawResult> draw(UUID userId, String gashaponProductId) {
        Integer drawCount = DRAW_COUNT_BY_PRODUCT_ID.get(gashaponProductId);
        if (drawCount == null) {
            throw new EntityNotFoundException("존재하지 않는 가샤폰 상품입니다: " + gashaponProductId);
        }

        Users user = userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다."));

        Product gashapon = productRepositoryPort.findActiveById(gashaponProductId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 상품입니다."));

        List<Product> pool = productRepositoryPort.findAllActiveExcludingType(ProductType.GASHAPON);
        if (pool.isEmpty()) {
            throw new EntityNotFoundException("뽑을 수 있는 상품이 없습니다.");
        }

        user.usePoint(gashapon.getPrice());

        // 이번 뽑기(같은 요청) 안에서 이미 지급한 상품을 기억해서,
        // 5연차 안에서 같은 상품이 두 번 나와도 두 번째는 "중복"으로 처리하고
        // 인벤토리에 두 번 insert 하려다 유니크 제약에 걸리는 걸 막는다.
        Set<String> grantedInThisDraw = new HashSet<>();
        List<GashaponDrawResult> results = new ArrayList<>();

        for (int i = 0; i < drawCount; i++) {
            Product picked = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));

            boolean duplicate = grantedInThisDraw.contains(picked.getProductId())
                    || inventoryRepositoryPort.existsByUserIdAndProductId(userId, picked.getProductId());

            if (!duplicate) {
                inventoryRepositoryPort.save(Inventory.of(user, picked));
                grantedInThisDraw.add(picked.getProductId());
            }

            results.add(new GashaponDrawResult(picked, duplicate));
        }

        return results;
    }
}
