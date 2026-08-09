package com.raon.tikitaka.application.auth;

public sealed interface LoginResult {

    record Registered(String accessToken, String refreshToken) implements LoginResult {
    }

    record SignupRequired(String signupToken) implements LoginResult {
    }
}
