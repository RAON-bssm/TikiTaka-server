package com.raon.tikitaka.application.match.out;

public interface MissionGeneratorPort {

    /**
     * 형용사+명사 조합으로 미션 문장 1개를 생성한다.
     * 구현체는 어떤 경우에도 예외를 던지지 말고 폴백 문장을 반환해야 한다 —
     * 외부 API 때문에 라운드가 안 열리는 일이 있어서는 안 된다.
     */
    String generate(String adjective, String noun);
}
