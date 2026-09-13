package com.raon.tikitaka.application.user.in;

import com.raon.tikitaka.domain.user.Users;

import java.util.UUID;

public interface GetMyInfoUseCase {

    /**
     * 토큰의 유저 본인 정보를 돌려준다. 소속 동네까지 함께 로딩한다.
     * 랭킹 강조와 마이페이지가 함께 쓴다.
     */
    Users getMyInfo(UUID userId);
}
