package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 점수 계산 설정 (application.yaml의 ranking: 블록).
 *
 * @param baseMultiplier K — 고정 배율 (67.76). 개인 점수 = round(AI점수 × K)
 * @param minTeamSize    조정치 분모의 하한 (5). 조정치 = C / max(n, minTeamSize)
 * @param dormantDays    휴면 전환 기준 일수 (7)
 */
@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(double baseMultiplier, int minTeamSize, int dormantDays) {
}
