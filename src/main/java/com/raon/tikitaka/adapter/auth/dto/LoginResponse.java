package com.raon.tikitaka.adapter.auth.dto;

public record LoginResponse(String status, String accessToken, String refreshToken, String signupToken) {

    public static LoginResponse loggedIn(String accessToken, String refreshToken) {
        return new LoginResponse("LOGIN", accessToken, refreshToken, null);
    }

    public static LoginResponse signupRequired(String signupToken) {
        return new LoginResponse("SIGNUP_REQUIRED", null, null, signupToken);
    }
}
