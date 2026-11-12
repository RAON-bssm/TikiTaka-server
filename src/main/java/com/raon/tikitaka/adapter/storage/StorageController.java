package com.raon.tikitaka.adapter.storage;

import com.raon.tikitaka.adapter.storage.dto.PresignedUrlResponse;
import com.raon.tikitaka.adapter.storage.dto.ViewUrlListResponse;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /**
     * 여러 key의 조회 URL을 한 번에 발급한다. key 파라미터를 반복해서 받는다(?key=a&key=b).
     * List<String>으로 바로 받으면 값 하나에 들어 있는 쉼표를 스프링이 나눠 버려서(key에 쉼표가 있을 수 있다)
     * 원래 값 그대로인 MultiValueMap에서 꺼낸다. key가 없을 때도 다른 400과 같은 응답 형식이 되도록
     * 필수 파라미터로 두지 않고 개수 검사는 유스케이스가 한다.
     */
    @GetMapping("/view-urls")
    public ApiResponse<ViewUrlListResponse> getViewUrls(@RequestParam MultiValueMap<String, String> params) {
        List<String> keys = params.getOrDefault("key", List.of());
        ViewUrlListResponse response = ViewUrlListResponse.from(storageUseCase.issueViewUrls(keys));
        return ApiResponse.of(200, "조회 URL 일괄 발급 성공", response);
    }
}
