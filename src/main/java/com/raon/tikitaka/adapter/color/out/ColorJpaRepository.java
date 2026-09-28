package com.raon.tikitaka.adapter.color.out;

import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ColorJpaRepository extends JpaRepository<Color, Long> {

    Optional<Color> findByColorGroupAndColorCode(ColorGroup colorGroup, String colorCode);
}
