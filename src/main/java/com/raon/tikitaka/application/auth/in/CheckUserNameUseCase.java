package com.raon.tikitaka.application.auth.in;

public interface CheckUserNameUseCase {

    /**
     * 가입 화면의 닉네임 중복확인. 토큰 발급 전에 호출되므로 인증을 요구하지 않는다.
     *
     * 여기서 true를 받아도 가입이 보장되지는 않는다. 확인 시점과 가입 시점 사이에
     * 다른 사람이 같은 닉네임으로 가입할 수 있으므로, 가입 요청의 409는 그대로 처리해야 한다.
     */
    boolean isUserNameAvailable(String userName);
}
