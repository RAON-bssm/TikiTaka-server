package com.raon.tikitaka.application.location.out;

import com.raon.tikitaka.domain.location.Location;

import java.util.List;
import java.util.Optional;

public interface LocationRepositoryPort {

    List<Location> findAll();

    long count();

    /**
     * 회원가입 시 mainLocationId 검증과 조회에 쓴다.
     */
    Optional<Location> findById(Long locationId);

    Location save(Location location);

    /**
     * 지역 등록 시 같은 이름이 이미 있는지 확인한다.
     */
    boolean existsByLocationName(String locationName);
}
