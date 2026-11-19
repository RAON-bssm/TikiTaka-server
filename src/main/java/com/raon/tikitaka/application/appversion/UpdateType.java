package com.raon.tikitaka.application.appversion;

/** 앱 업데이트 필요 여부. 응답에는 이름(NONE, OPTIONAL, FORCE) 그대로 나간다 */
public enum UpdateType {
    /** 업데이트 필요 없음 */
    NONE,
    /** 업데이트 권장. 앱은 닫을 수 있는 안내를 띄운다 */
    OPTIONAL,
    /** 업데이트 강제. 최소 지원 버전보다 낮다 */
    FORCE
}
