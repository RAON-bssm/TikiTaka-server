package com.raon.tikitaka.adapter.location.dto;

import com.raon.tikitaka.domain.location.Location;

import java.util.ArrayList;
import java.util.List;

public record LocationListResponse(List<LocationResponse> locations) {

    public static LocationListResponse from(List<Location> locations) {
        List<LocationResponse> responses = new ArrayList<>();
        for (Location location : locations) {
            responses.add(LocationResponse.from(location));
        }
        return new LocationListResponse(responses);
    }
}
