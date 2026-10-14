package com.raon.tikitaka.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportReason {

    SPAM("스팸·광고"),
    ABUSE("욕설·혐오·비방"),
    SEXUAL("음란·선정적"),
    PRIVACY("개인정보·초상권 침해"),
    FAKE_MISSION("미션과 무관하거나 조작된 인증"),   // 게시물 신고 전용. 유저 신고에서는 받지 않는다
    OTHER("기타");

    private final String description;
}
