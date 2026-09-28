package com.raon.tikitaka.adapter.color.out;

import com.raon.tikitaka.application.color.out.ColorRepositoryPort;
import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ColorPersistenceAdapter implements ColorRepositoryPort {

    private final ColorJpaRepository colorJpaRepository;

    @Override
    public Optional<Color> findByColorGroupAndColorCode(ColorGroup colorGroup, String colorCode) {
        return colorJpaRepository.findByColorGroupAndColorCode(colorGroup, colorCode);
    }
}
