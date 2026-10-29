package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 땅따먹기 설정. application.yaml에 territory 블록이 없으면 기본값을 쓴다.
 *
 * @param rankingDays 포인트 라운드 시작 후 개인·동네 랭킹이 쌓이는 일수. 이 시각부터 랭킹이 동결된다
 */
@ConfigurationProperties(prefix = "territory")
public record TerritoryProperties(@DefaultValue("4") int rankingDays) {
}
