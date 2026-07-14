package com.raon.tikitaka.adapter.inventory.out;

import com.raon.tikitaka.domain.userItem.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface InventoryJpaRepository extends JpaRepository<Inventory, Long> {

    @Query("""
            select i from Inventory i
            join fetch i.product
            where i.user.userId = :userId
            """)
    List<Inventory> findAllByUserIdWithProduct(UUID userId);
}
