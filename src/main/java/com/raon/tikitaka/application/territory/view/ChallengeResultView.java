package com.raon.tikitaka.application.territory.view;

import com.raon.tikitaka.domain.enums.ChallengeResult;

import java.time.LocalDateTime;
import java.util.UUID;

/** 도전 결과. myPoint(통행료를 낸 뒤 내 포인트)는 도전 직후 응답에만 들어간다 */
public record ChallengeResultView(UUID challengeId, Long dongCode, String dongName, String mission,
                                  ChallengeResult result, int score, int defenseScoreAt, int toll,
                                  String review, LocalDateTime createdAt, Integer myPoint) {
}
