package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.domain.enums.ProductType;
import com.raon.tikitaka.domain.userItem.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EquipmentJpaRepository extends JpaRepository<Equipment, Long> {

    @Query("""
            select e from Equipment e
            join fetch e.product
            where e.user.userId = :userId
            """)
    List<Equipment> findAllByUserIdWithProduct(UUID userId);

    @Modifying
    @Query("""
            delete from Equipment e
            where e.user.userId = :userId and e.product.productType in :productTypes
            """)
    void deleteAllByUser_UserIdAndProduct_ProductTypeIn(@Param("userId") UUID userId, @Param("productTypes") Collection<ProductType> productTypes);
}
