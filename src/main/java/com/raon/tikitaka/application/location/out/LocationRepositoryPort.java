package com.raon.tikitaka.application.location.out;

import com.raon.tikitaka.domain.location.Location;

import java.util.List;
import java.util.Optional;

public interface LocationRepositoryPort {

    List<Location> findAll();

    long count();

    /**
     * 회원가입(동네 필수)에서 mainLocationId 검증·조회에 쓴다.
     */
    Optional<Location> findById(Long locationId);
}
