package com.raon.tikitaka.application.territory.policy;

import com.raon.tikitaka.global.config.TerritoryProperties;
import org.springframework.stereotype.Component;

/** 통행료 = floor(땅값 × rate). 대표 몫 = floor(통행료 × ownerShare), 나머지는 소멸 */
@Component
public class TollPolicy {

    private final TerritoryProperties.Toll config;

    public TollPolicy(TerritoryProperties properties) {
        this.config = properties.toll();
    }

    public int toll(int landPrice) {
        return (int) Math.floor(landPrice * config.rate());
    }

    public int ownerShare(int toll) {
        return (int) Math.floor(toll * config.ownerShare());
    }
}
