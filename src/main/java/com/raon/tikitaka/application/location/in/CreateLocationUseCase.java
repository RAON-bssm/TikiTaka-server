package com.raon.tikitaka.application.location.in;

import com.raon.tikitaka.domain.location.Location;

public interface CreateLocationUseCase {

    /**
     * 지역을 새로 등록한다.
     * 같은 이름이 이미 있으면 DuplicateLocationNameException을 던진다.
     */
    Location createLocation(String locationName);
}
