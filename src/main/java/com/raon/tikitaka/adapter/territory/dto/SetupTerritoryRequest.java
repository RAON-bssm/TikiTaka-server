package com.raon.tikitaka.adapter.territory.dto;

import org.springframework.web.multipart.MultipartFile;

/** multipart/form-data. 필드 이름은 게시글 작성(CreatePostRequest)처럼 camelCase 그대로 받는다 */
public record SetupTerritoryRequest(String adjective, String noun, String scoringCriteria, MultipartFile image) {
}
