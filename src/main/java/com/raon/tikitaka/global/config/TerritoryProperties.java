package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 땅따먹기 설정. application.yaml에 territory 블록이 없으면 아래 기본값을 쓴다.
 * 지금은 선발전과 대표 선발에 쓰는 값만 둔다. 통행료·개발·방치 감쇠 값은 해당 기능과 함께 추가한다.
 *
 * @param rankingDays 포인트 라운드 시작 후 개인·동네 랭킹이 쌓이는 일수
 * @param maxOwned    한 사람이 가질 수 있는 최대 동 수
 */
@ConfigurationProperties(prefix = "territory")
public record TerritoryProperties(
        @DefaultValue("4") int rankingDays,
        @DefaultValue("5") int maxOwned,
        @DefaultValue LandPrice landPrice,
        @DefaultValue Defense defense) {

    /** 사이클 첫날의 기본 땅값 */
    public record LandPrice(@DefaultValue("2500") int base) {
    }

    /** 주인 없는 땅의 기준 방어 점수 */
    public record Defense(@DefaultValue("60") int unownedMinScore) {
    }
}
