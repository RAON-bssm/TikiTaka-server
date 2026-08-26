package com.raon.tikitaka.adapter.keyword;

import com.raon.tikitaka.adapter.keyword.dto.KeywordListResponse;
import com.raon.tikitaka.adapter.keyword.dto.KeywordRequest;
import com.raon.tikitaka.application.match.in.ManageKeywordUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미션 키워드 관리 API. 경로가 /api/admin/** 이라 ADMIN 역할만 접근할 수 있다.
 * 추가와 삭제는 다음 라운드의 미션 생성부터 반영되고 이미 만들어진 미션은 그대로 둔다.
 */
@RestController
@RequestMapping("/api/admin/keyword")
@RequiredArgsConstructor
public class KeywordAdminController {

    private final ManageKeywordUseCase manageKeywordUseCase;

    @GetMapping
    public ApiResponse<KeywordListResponse> getKeywords() {
        return ApiResponse.of(200, "키워드 목록 조회 성공",
                KeywordListResponse.from(manageKeywordUseCase.getKeywords()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> addKeyword(@RequestBody KeywordRequest request) {
        manageKeywordUseCase.addKeyword(request.keyword(), request.type());
        return ApiResponse.of(201, "키워드 추가 성공", null);
    }

    @DeleteMapping("/{keyword}")
    public ApiResponse<Void> removeKeyword(@PathVariable String keyword) {
        manageKeywordUseCase.removeKeyword(keyword);
        return ApiResponse.of(200, "키워드 삭제 성공", null);
    }
}
