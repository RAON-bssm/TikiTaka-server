package com.raon.tikitaka.adapter.product.out;

import com.raon.tikitaka.domain.enums.ProductType;
import com.raon.tikitaka.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, String> {

    @Query("""
            select p from Product p
            where p.isActive = true
            """)
    List<Product> findAllActiveProducts();

    @Query("""
            select p from Product p
            where p.productId = :productId and p.isActive = true
            """)
    Optional<Product> findActiveById(String productId);

    /**
     * 상점 목록/가샤폰 뽑기 풀 공용. 가격 0원(기본 지급용) 아이템은 유료 컨텐츠가 아니므로
     * 여기서 제외한다 — 회원가입 시 자동 지급되는 것 말고는 얻을 방법이 없어야 정상이다.
     */
    @Query("""
            select p from Product p
            where p.isActive = true and p.productType <> :productType and p.price > 0
            """)
    List<Product> findAllActiveExcludingType(ProductType productType);

    /**
     * 회원가입 시 자동 지급할 기본(가격 0원) 아이템 목록. 가샤폰은 무료로 지급할 대상이 아니라 제외한다.
     */
    @Query("""
            select p from Product p
            where p.isActive = true and p.productType <> :productType and p.price = 0
            """)
    List<Product> findAllActiveFreeItemsExcludingType(ProductType productType);
}
