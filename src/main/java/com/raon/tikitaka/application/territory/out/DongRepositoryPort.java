package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.Dong;

import java.util.List;

public interface DongRepositoryPort {

    /** 한 구의 동 목록. 대표 선발(동 수)과 희망 동 검증에 쓴다 */
    List<Dong> findAllByLocationId(Long locationId);
}
