package com.raon.tikitaka.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MatchType {

    NORMAL("일반 매치"),
    EVENT("이벤트 매치"),
    BYE("미션 위크");

    private final String description;
}
