package com.raon.tikitaka.domain.enums;

/** 땅따먹기 사이클 안에서 라운드(Stage)의 역할. 사이클에 속하지 않는 라운드는 NONE이다 */
public enum RoundType {
    POINT,   // 1주차: 게시판 + 대표 선발 + 땅 세팅
    BATTLE,  // 2주차: 땅따먹기
    NONE
}
