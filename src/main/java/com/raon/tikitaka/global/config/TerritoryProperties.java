package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 땅따먹기 설정. application.yaml에 territory 블록이 없으면 아래 기본값을 쓴다.
 * 결정이 남은 값(#13 개발 상승폭·횟수, #14 통행료율)은 후보값이다. 땅값 상한은 두지 않는다.
 *
 * @param rankingDays 포인트 라운드 시작 후 개인·동네 랭킹이 쌓이는 일수
 * @param maxOwned    한 사람이 가질 수 있는 최대 동 수. 0 이하면 제한 없음
 */
@ConfigurationProperties(prefix = "territory")
public record TerritoryProperties(
        @DefaultValue("4") int rankingDays,
        @DefaultValue("0") int maxOwned,
        @DefaultValue Toll toll,
        @DefaultValue LandPrice landPrice,
        @DefaultValue Defense defense,
        @DefaultValue Development development,
        @DefaultValue Wage wage) {

    /** 제한이 있는지. maxOwned가 0 이하면 몇 개든 가질 수 있다 */
    public boolean hasOwnedLimit() {
        return maxOwned > 0;
    }

    /** 통행료 = floor(땅값 × rate), 대표 몫 = floor(통행료 × ownerShare). 1.0이면 주인이 전액 받는다 */
    public record Toll(@DefaultValue("0.15") double rate, @DefaultValue("1.0") double ownerShare) {
    }

    /** 기본 땅값 = floor(base × clamp(공격 수 ÷ 보드 평균, popularityMin, popularityMax)) */
    public record LandPrice(@DefaultValue("2500") int base,
                            @DefaultValue("0.5") double popularityMin,
                            @DefaultValue("3.0") double popularityMax,
                            @DefaultValue("7") int windowDays) {
    }

    /** 주인 없는 땅 기준 점수, 감쇠(주인 유무와 상관없이 모든 땅을 하루 decayPerDay씩, decayFloor 아래로는 안 내려감) */
    public record Defense(@DefaultValue("60") int unownedMinScore,
                          @DefaultValue("2") int decayPerDay,
                          @DefaultValue("40") int decayFloor) {
    }

    /**
     * 개발비 = floor(땅값 × costRate), 성공 확률 probability 고정, 성공 시 땅값 += floor(현재 땅값 × gainRate).
     * dailyLimitPerUser는 유저 1명의 하루 개발 시도 수(0 이하면 제한 없음), dailyGainLimitPerDong은 땅 1개의 하루 성공 수
     */
    public record Development(@DefaultValue("1.65") double costRate,
                              @DefaultValue("0.49") double probability,
                              @DefaultValue("0.25") double gainRate,
                              @DefaultValue("0") int dailyLimitPerUser,
                              @DefaultValue("3") int dailyGainLimitPerDong) {

        public boolean hasUserDailyLimit() {
            return dailyLimitPerUser > 0;
        }
    }

    /** 일급 = floor(그 유저가 가진 땅의 땅값 합계 × rate). 땅따먹기 라운드 동안 매일 땅 주인에게 지급 */
    public record Wage(@DefaultValue("0.5") double rate) {
    }
}
