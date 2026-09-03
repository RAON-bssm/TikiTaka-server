package com.raon.tikitaka.adapter.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocationCreateRequest(

        @NotBlank(message = "지역 이름은 필수입니다.")
        @Size(max = 50, message = "지역 이름은 50자를 넘을 수 없습니다.")
        String locationName
) {
}
