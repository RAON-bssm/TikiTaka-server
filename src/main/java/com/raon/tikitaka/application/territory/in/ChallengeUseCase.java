package com.raon.tikitaka.application.territory.in;

import com.raon.tikitaka.application.territory.view.ChallengeResultView;

import java.util.UUID;

public interface ChallengeUseCase {

    record Command(byte[] image, String fileName, String contentType, Double lat, Double lng) {
    }

    /** 땅따먹기 주간에 상대 팀 땅이나 주인 없는 땅에 도전한다. 통행료는 결과와 관계없이 낸다 */
    ChallengeResultView challenge(UUID userId, Long dongCode, Command command);

    /** 내 도전 결과 재조회 */
    ChallengeResultView getResult(UUID userId, UUID challengeId);
}
