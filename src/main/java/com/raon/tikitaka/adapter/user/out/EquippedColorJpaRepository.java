package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.userItem.EquippedColor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EquippedColorJpaRepository extends JpaRepository<EquippedColor, Long> {

    @Query("""
            select ec from EquippedColor ec
            join fetch ec.color
            where ec.user.userId = :userId
            """)
    List<EquippedColor> findAllByUserIdWithColor(UUID userId);

    /**
     * 여러 유저의 착용 색을 한 번에 읽는다. findAllByUserIdsWithProduct와 짝으로 쓴다.
     */
    @Query("""
            select ec from EquippedColor ec
            join fetch ec.color
            where ec.user.userId in :userIds
            """)
    List<EquippedColor> findAllByUserIdsWithColor(@Param("userIds") Collection<UUID> userIds);

    @Modifying
    @Query("""
            delete from EquippedColor ec
            where ec.user.userId = :userId and ec.colorGroup in :colorGroups
            """)
    void deleteAllByUser_UserIdAndColorGroupIn(@Param("userId") UUID userId, @Param("colorGroups") Collection<ColorGroup> colorGroups);
}
