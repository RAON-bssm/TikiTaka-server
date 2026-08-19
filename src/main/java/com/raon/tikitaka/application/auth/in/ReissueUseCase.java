package com.raon.tikitaka.application.auth.in;

import com.raon.tikitaka.application.auth.TokenResult;

public interface ReissueUseCase {

    TokenResult reissue(String refreshToken);
}
