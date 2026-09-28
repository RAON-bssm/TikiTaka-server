package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.userItem.EquippedColor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    void deleteAllByUser_UserIdAndColorGroupIn(UUID userId, Collection<ColorGroup> colorGroups);
}
