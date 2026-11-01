package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.BlockedUserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BlockedUserAdapter implements BlockedUserPort {

    private final TerritoryBlockJpaRepository territoryBlockJpaRepository;

    @Override
    public Set<UUID> findBlockedUserIds(UUID blockerId) {
        return new HashSet<>(territoryBlockJpaRepository.findBlockedUserIds(blockerId));
    }
}
