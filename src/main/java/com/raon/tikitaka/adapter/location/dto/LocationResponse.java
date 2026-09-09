package com.raon.tikitaka.adapter.location.dto;

import com.raon.tikitaka.domain.location.Location;

public record LocationResponse(Long locationId, String cityName, String locationName) {

    public static LocationResponse from(Location location) {
        return new LocationResponse(location.getLocationId(), location.getCityName(), location.getLocationName());
    }
}
