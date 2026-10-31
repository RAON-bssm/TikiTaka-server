package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.DongRepositoryPort;
import com.raon.tikitaka.domain.territory.Dong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DongPersistenceAdapter implements DongRepositoryPort {

    private final DongJpaRepository dongJpaRepository;

    @Override
    public List<Dong> findAllByLocationId(Long locationId) {
        return dongJpaRepository.findAllByLocation_LocationIdOrderByDongCode(locationId);
    }
}
