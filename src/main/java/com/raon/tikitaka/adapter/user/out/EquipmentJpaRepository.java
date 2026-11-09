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

    /**
     * 여러 유저의 착용 상품을 한 번에 읽는다. 글 목록·랭킹처럼 작성자가 여럿인 응답에서 유저마다 따로 조회하지 않기 위해서다.
     */
    @Query("""
            select e from Equipment e
            join fetch e.product
            where e.user.userId in :userIds
            """)
    List<Equipment> findAllByUserIdsWithProduct(@Param("userIds") Collection<UUID> userIds);

    @Modifying
    @Query("""
            delete from Equipment e
            where e.user.userId = :userId and e.product.productType in :productTypes
            """)
    void deleteAllByUser_UserIdAndProduct_ProductTypeIn(@Param("userId") UUID userId, @Param("productTypes") Collection<ProductType> productTypes);
}
