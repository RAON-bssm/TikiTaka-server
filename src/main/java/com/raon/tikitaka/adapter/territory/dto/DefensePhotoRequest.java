package com.raon.tikitaka.adapter.territory.dto;

import org.springframework.web.multipart.MultipartFile;

/** multipart/form-data. 필드 이름은 image */
public record DefensePhotoRequest(MultipartFile image) {
}
