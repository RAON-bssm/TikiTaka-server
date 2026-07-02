package com.raon.tikitaka.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LoginProvider {
    KAKAO("카카오 로그인"),
    GOOGLE("구글 로그인");

    private final String description;
}