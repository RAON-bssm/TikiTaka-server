package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.DongRepositoryPort;
import com.raon.tikitaka.domain.territory.Dong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DongPersistenceAdapter implements DongRepositoryPort {

    private final DongJpaRepository dongJpaRepository;

    @Override
    public Optional<Dong> findById(Long dongId) {
        return dongJpaRepository.findById(dongId);
    }

    @Override
    public List<Dong> findAllByLocationId(Long locationId) {
        return dongJpaRepository.findAllByLocation_LocationIdOrderByDongCode(locationId);
    }

    @Override
    public List<Dong> findAll() {
        return dongJpaRepository.findAll();
    }

    @Override
    public long countByLocationId(Long locationId) {
        return dongJpaRepository.countByLocation_LocationId(locationId);
    }
}
