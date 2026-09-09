package com.raon.tikitaka.adapter.auth.dto;

public record UserNameAvailabilityResponse(boolean available) {

    public static UserNameAvailabilityResponse of(boolean available) {
        return new UserNameAvailabilityResponse(available);
    }
}
