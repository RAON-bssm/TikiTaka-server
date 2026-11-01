package com.raon.tikitaka.application.territory.policy;

import com.raon.tikitaka.global.config.TerritoryProperties;
import org.springframework.stereotype.Component;

/**
 * 기본 땅값 = floor(base × 인기도). 인기도 = clamp(그 동의 공격 수 ÷ 보드 평균, min, max).
 * 공격 수는 최근 windowDays일의 (공격자, 날짜) 쌍의 수다. 보드 평균이 0이면(첫날 등) 인기도 1.0(결정 #18 제안).
 */
@Component
public class LandPricePolicy {

    private final TerritoryProperties.LandPrice config;

    public LandPricePolicy(TerritoryProperties properties) {
        this.config = properties.landPrice();
    }

    public double popularity(long attackCount, double boardAverage) {
        if (boardAverage <= 0) {
            return 1.0;
        }
        double raw = attackCount / boardAverage;
        return Math.max(config.popularityMin(), Math.min(config.popularityMax(), raw));
    }

    public int basePrice(long attackCount, double boardAverage) {
        return (int) Math.floor(config.base() * popularity(attackCount, boardAverage));
    }

    /** 공격 기록이 없는 사이클 첫날의 기본 땅값 */
    public int initialPrice() {
        return config.base();
    }
}
