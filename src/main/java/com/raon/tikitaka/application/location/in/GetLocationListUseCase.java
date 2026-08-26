package com.raon.tikitaka.application.location.in;

import com.raon.tikitaka.domain.location.Location;

import java.util.List;

public interface GetLocationListUseCase {

    /**
     * 가입 화면의 동네 선택에 쓰는 전체 지역 목록.
     * 가입 요청의 mainLocationId는 이 목록의 locationId여야 한다.
     */
    List<Location> getLocations();
}
