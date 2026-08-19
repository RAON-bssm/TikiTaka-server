package com.raon.tikitaka.application.auth.out;

import com.raon.tikitaka.application.auth.SocialUserInfo;
import com.raon.tikitaka.domain.enums.LoginProvider;

public interface OAuthClientPort {

    SocialUserInfo fetchUserInfo(LoginProvider provider, String providerAccessToken);
}
