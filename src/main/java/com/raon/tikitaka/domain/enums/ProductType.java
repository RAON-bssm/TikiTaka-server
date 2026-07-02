package com.raon.tikitaka.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductType {
    BODY("몸"),
    ACCESSORY("액세서리"),
    CLOTHING("옷"),
    EYES("눈"),
    HAIR_FRONT("앞머리"),
    HAIR_BACK("뒷머리"),
    MOUTH("입");

    private final String description;
}