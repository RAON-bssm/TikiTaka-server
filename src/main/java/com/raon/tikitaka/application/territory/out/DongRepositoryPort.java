package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.Dong;

import java.util.List;
import java.util.Optional;

public interface DongRepositoryPort {

    Optional<Dong> findById(Long dongId);

    /** 한 구의 동 목록. 대표 선발(동 수)과 희망 동 검증에 쓴다 */
    List<Dong> findAllByLocationId(Long locationId);

    List<Dong> findAll();

    /** 구별 동 수. 대표 선발에서 구마다 몇 명을 뽑을지 정한다 */
    long countByLocationId(Long locationId);
}
