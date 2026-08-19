package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시즌과 라운드 주기 설정. application.yaml의 season 블록과 매핑된다.
 *
 * @param roundDays          라운드 하나의 길이
 * @param roundsPerSeason    시즌당 라운드 수
 * @param prefetchDays       미래 라운드를 며칠치 미리 만들어둘지
 * @param matchOpenAheadDays 매치와 게시판을 라운드 시작 며칠 전에 만들어둘지
 */
@ConfigurationProperties(prefix = "season")
public record SeasonProperties(int roundDays, int roundsPerSeason, int prefetchDays, int matchOpenAheadDays) {
}
