package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 땅따먹기 설정. application.yaml에 territory 블록이 없으면 아래 기본값을 쓴다.
 * 결정이 남은 값(#13 개발 상승폭·횟수, #14 통행료율)은 후보값이다. 땅값 상한은 두지 않는다.
 *
 * @param rankingDays 포인트 라운드 시작 후 개인·동네 랭킹이 쌓이는 일수
 * @param maxOwned    한 사람이 가질 수 있는 최대 동 수
 */
@ConfigurationProperties(prefix = "territory")
public record TerritoryProperties(
        @DefaultValue("4") int rankingDays,
        @DefaultValue("5") int maxOwned,
        @DefaultValue Toll toll,
        @DefaultValue LandPrice landPrice,
        @DefaultValue Defense defense,
        @DefaultValue Development development) {

    /** 통행료 = floor(땅값 × rate), 대표 몫 = floor(통행료 × ownerShare) */
    public record Toll(@DefaultValue("0.15") double rate, @DefaultValue("0.90") double ownerShare) {
    }

    /** 기본 땅값 = floor(base × clamp(공격 수 ÷ 보드 평균, popularityMin, popularityMax)) */
    public record LandPrice(@DefaultValue("2500") int base,
                            @DefaultValue("0.5") double popularityMin,
                            @DefaultValue("3.0") double popularityMax,
                            @DefaultValue("7") int windowDays) {
    }

    /** 주인 없는 땅 기준 점수, 방치 감쇠(하루 decayPerDay, idleDays일 비활동부터, decayFloor 아래로는 안 내려감) */
    public record Defense(@DefaultValue("60") int unownedMinScore,
                          @DefaultValue("1") int decayPerDay,
                          @DefaultValue("1") int idleDays,
                          @DefaultValue("60") int decayFloor) {
    }

    /** 개발비 = floor(땅값 × costRate), 성공 확률 probability 고정, 성공 시 +gain */
    public record Development(@DefaultValue("1.8") double costRate,
                              @DefaultValue("0.49") double probability,
                              @DefaultValue("250") int gain,
                              @DefaultValue("3") int dailyLimitPerUser,
                              @DefaultValue("5") int dailyGainLimitPerDong) {
    }
}
