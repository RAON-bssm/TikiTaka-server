package com.raon.tikitaka.application.auth;

import com.raon.tikitaka.domain.enums.LoginProvider;

public record SocialUserInfo(LoginProvider provider, String providerId) {
}
