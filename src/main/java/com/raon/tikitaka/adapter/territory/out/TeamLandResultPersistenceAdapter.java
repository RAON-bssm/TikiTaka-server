package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.TeamLandResultPort;
import com.raon.tikitaka.domain.territory.TeamLandResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TeamLandResultPersistenceAdapter implements TeamLandResultPort {

    private final TeamLandResultJpaRepository repository;

    @Override
    public void saveAll(List<TeamLandResult> results) {
        repository.saveAll(results);
    }

    @Override
    public List<TeamLandResult> findAllByCycleId(Long cycleId) {
        return repository.findAllByCycleId(cycleId);
    }
}
