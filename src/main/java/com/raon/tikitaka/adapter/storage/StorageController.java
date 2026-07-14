package com.raon.tikitaka.adapter.storage;

import com.raon.tikitaka.adapter.storage.dto.PresignedUrlResponse;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageUseCase storageUseCase;

    @GetMapping("/upload-url")
    public ApiResponse<PresignedUrlResponse> getUploadUrl(@RequestParam String fileName) {
        PresignedUrlResponse response = PresignedUrlResponse.from(storageUseCase.issueUploadUrl(fileName));
        return ApiResponse.of(200, "업로드 URL 발급 성공", response);
    }

    @GetMapping("/view-url")
    public ApiResponse<PresignedUrlResponse> getViewUrl(@RequestParam String key) {
        PresignedUrlResponse response = PresignedUrlResponse.from(storageUseCase.issueViewUrl(key));
        return ApiResponse.of(200, "조회 URL 발급 성공", response);
    }
}
