package com.raon.tikitaka.application.match.out;

public interface MissionGeneratorPort {

    /**
     * 형용사와 명사 조합으로 미션 문장 1개를 생성한다.
     * 외부 API 때문에 라운드가 안 열리면 안 되므로
     * 구현체는 어떤 경우에도 예외를 던지지 말고 대체 문장을 반환해야 한다.
     */
    String generate(String adjective, String noun);
}
