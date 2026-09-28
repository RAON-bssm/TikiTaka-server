package com.raon.tikitaka.application.color.out;

import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;

import java.util.Optional;

public interface ColorRepositoryPort {

    Optional<Color> findByColorGroupAndColorCode(ColorGroup colorGroup, String colorCode);
}
