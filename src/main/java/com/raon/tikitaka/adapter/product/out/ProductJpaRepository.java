package com.raon.tikitaka.adapter.product.out;

import com.raon.tikitaka.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    @Query("""
            select p from Product p
            where p.isActive = true
            """)
    List<Product> findAllActiveProducts();

    @Query("""
            select p from Product p
            where p.productId = :productId and p.isActive = true
            """)
    Optional<Product> findActiveById(Long productId);
}
