package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.DefaultMission;

import java.util.List;

public interface DefaultMissionPort {
    List<DefaultMission> findAll();
}
