package com.raon.tikitaka.application.location.out;

import com.raon.tikitaka.domain.location.Location;

import java.util.List;

public interface LocationRepositoryPort {

    List<Location> findAll();

    long count();
}
