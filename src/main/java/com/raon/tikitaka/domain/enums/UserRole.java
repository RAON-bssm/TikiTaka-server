package com.raon.tikitaka.domain.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum UserRole {
    USER("유저"),
    ADMIN("관리자");

    private final String description;
}