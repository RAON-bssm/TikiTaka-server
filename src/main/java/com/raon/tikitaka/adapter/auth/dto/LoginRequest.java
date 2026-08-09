package com.raon.tikitaka.adapter.auth.dto;

import com.raon.tikitaka.domain.enums.LoginProvider;

public record LoginRequest(LoginProvider provider, String providerAccessToken) {
}
