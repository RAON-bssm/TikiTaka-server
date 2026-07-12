package com.raon.tikitaka.adapter.equipment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record EquipRequest(
        @JsonProperty("equipment_ids") List<Long> equipmentIds
) {
}
