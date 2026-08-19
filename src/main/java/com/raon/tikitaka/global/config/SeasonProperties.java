package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시즌/라운드 주기 설정 (application.yaml의 season: 블록).
 *
 * @param roundDays          라운드 1개 길이 (7일)
 * @param roundsPerSeason    시즌당 라운드 수 (4 → 시즌 = 28일)
 * @param prefetchDays       미래 라운드(Stage 행)를 며칠치 미리 만들어둘지 (30)
 * @param matchOpenAheadDays 매치·게시판을 라운드 시작 며칠 전에 만들어둘지 (1)
 */
@ConfigurationProperties(prefix = "season")
public record SeasonProperties(int roundDays, int roundsPerSeason, int prefetchDays, int matchOpenAheadDays) {
}
