package com.raon.tikitaka.application.auth.in;

import com.raon.tikitaka.application.auth.LoginResult;
import com.raon.tikitaka.domain.enums.LoginProvider;

public interface LoginUseCase {

    LoginResult login(LoginProvider provider, String providerAccessToken);
}
