package com.raon.tikitaka.adapter.location.out;

import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.domain.location.Location;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LocationPersistenceAdapter implements LocationRepositoryPort {

    private final LocationJpaRepository locationJpaRepository;

    @Override
    public List<Location> findAll() {
        return locationJpaRepository.findAll();
    }

    @Override
    public long count() {
        return locationJpaRepository.count();
    }

    @Override
    public Optional<Location> findById(Long locationId) {
        return locationJpaRepository.findById(locationId);
    }
}
