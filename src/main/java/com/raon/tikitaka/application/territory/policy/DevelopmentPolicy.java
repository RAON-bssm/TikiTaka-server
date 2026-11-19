package com.raon.tikitaka.application.territory.policy;

import com.raon.tikitaka.global.config.TerritoryProperties;
import org.springframework.stereotype.Component;

/** 개발비 = floor(땅값 × costRate). 성공 확률은 매번 같다(천장 없음). 성공하면 땅값 × gainRate만큼 오른다 */
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

    /** 성공 시 늘어나는 땅값 = floor(현재 땅값 × gainRate). 땅값이 오를수록 같은 비율로 커진다 */
    public int gain(int landPrice) {
        return (int) Math.floor(landPrice * config.gainRate());
    }
}
