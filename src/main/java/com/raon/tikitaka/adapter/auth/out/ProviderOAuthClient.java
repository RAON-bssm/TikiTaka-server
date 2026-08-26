package com.raon.tikitaka.adapter.auth.out;

import com.raon.tikitaka.application.auth.SocialUserInfo;
import com.raon.tikitaka.domain.enums.LoginProvider;

public interface ProviderOAuthClient {

    LoginProvider getProvider();

    SocialUserInfo fetchUserInfo(String providerAccessToken);
}
