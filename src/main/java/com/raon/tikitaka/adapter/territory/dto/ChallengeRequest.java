package com.raon.tikitaka.adapter.territory.dto;

import org.springframework.web.multipart.MultipartFile;

/** multipart/form-data. lat·lng는 선택이고 기록만 한다(서버는 위치를 검증하지 않는다) */
public record ChallengeRequest(MultipartFile image, Double lat, Double lng) {
}
