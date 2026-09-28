package com.raon.tikitaka.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 색상 커스터마이징이 가능한 파츠 그룹.
 * 앞머리/뒷머리는 항상 같은 색을 쓰므로 HAIR 하나로 묶고, 눈은 별도의 EYES 팔레트를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum ColorGroup {
    HAIR("머리"),
    EYES("눈");

    private final String description;
}
