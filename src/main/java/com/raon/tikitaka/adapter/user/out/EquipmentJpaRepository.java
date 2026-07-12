package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.domain.userItem.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EquipmentJpaRepository extends JpaRepository<Equipment, Long> {

    @Query("""
            select e from Equipment e
            join fetch e.product
            where e.user.userId = :userId
            """)
    List<Equipment> findAllByUserIdWithProduct(UUID userId);

    void deleteAllByUser_UserId(UUID userId);
}
