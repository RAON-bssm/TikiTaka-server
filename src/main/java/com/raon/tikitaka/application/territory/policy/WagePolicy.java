package com.raon.tikitaka.application.territory.policy;

import com.raon.tikitaka.global.config.TerritoryProperties;
import org.springframework.stereotype.Component;

/** 일급 = floor(가진 땅의 땅값 합계 × rate) */
@Component
public class WagePolicy {

    private final TerritoryProperties.Wage config;

    public WagePolicy(TerritoryProperties properties) {
        this.config = properties.wage();
    }

    public double rate() {
        return config.rate();
    }

    public int wage(long totalLandPrice) {
        return (int) Math.floor(totalLandPrice * config.rate());
    }
}
