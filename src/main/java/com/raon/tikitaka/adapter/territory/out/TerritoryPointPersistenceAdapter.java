package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.TerritoryPointPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TerritoryPointPersistenceAdapter implements TerritoryPointPort {

    private final TerritoryUserJpaRepository userJpaRepository;

    @Override
    public int getPoint(UUID userId) {
        Integer point = userJpaRepository.findPoint(userId);
        return point == null ? 0 : point;
    }

    @Override
    public boolean deduct(UUID userId, int amount) {
        return amount <= 0 || userJpaRepository.deductPoint(userId, amount) == 1;
    }

    @Override
    public void add(UUID userId, int amount) {
        if (amount > 0) {
            userJpaRepository.addPoint(userId, amount);
        }
    }
}
