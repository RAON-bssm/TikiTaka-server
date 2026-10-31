package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.DefaultMissionPort;
import com.raon.tikitaka.domain.territory.DefaultMission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultMissionPersistenceAdapter implements DefaultMissionPort {

    private final DefaultMissionJpaRepository defaultMissionJpaRepository;

    @Override
    public List<DefaultMission> findAll() {
        return defaultMissionJpaRepository.findAll();
    }
}
