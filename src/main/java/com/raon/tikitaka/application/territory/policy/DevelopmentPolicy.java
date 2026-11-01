package com.raon.tikitaka.application.territory.policy;

import com.raon.tikitaka.global.config.TerritoryProperties;
import org.springframework.stereotype.Component;

/** 개발비 = floor(땅값 × costRate). 성공 확률은 매번 같다(천장 없음). 성공하면 항상 같은 폭(gain) */
@Component
public class DevelopmentPolicy {

    private final TerritoryProperties.Development config;

    public DevelopmentPolicy(TerritoryProperties properties) {
        this.config = properties.development();
    }

    public int cost(int landPrice) {
        return (int) Math.floor(landPrice * config.costRate());
    }

    public double probability() {
        return config.probability();
    }

    /** roll은 [0, 1) 난수 */
    public boolean isSuccess(double roll) {
        return roll < config.probability();
    }

    public int gain() {
        return config.gain();
    }
}
