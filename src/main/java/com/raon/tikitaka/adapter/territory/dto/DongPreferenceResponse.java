package com.raon.tikitaka.adapter.territory.dto;

import com.raon.tikitaka.domain.territory.DongPreference;

import java.util.List;

public record DongPreferenceResponse(List<Item> preferences) {

    public record Item(int priority, Long dongId, Long dongCode, String dongName) {
    }

    public static DongPreferenceResponse from(List<DongPreference> preferences) {
        return new DongPreferenceResponse(preferences.stream()
                .map(p -> new Item(p.getPriority(), p.getDong().getDongId(), p.getDong().getDongCode(),
                        p.getDong().getDongName()))
                .toList());
    }
}
