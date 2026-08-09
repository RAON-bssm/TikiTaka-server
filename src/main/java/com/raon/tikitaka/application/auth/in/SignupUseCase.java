package com.raon.tikitaka.application.auth.in;

import com.raon.tikitaka.application.auth.TokenResult;

public interface SignupUseCase {

    TokenResult signup(String signupToken, String userName);
}
