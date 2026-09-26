package com.raon.tikitaka.adapter.user;

import com.raon.tikitaka.adapter.post.dto.MyPostListResponse;
import com.raon.tikitaka.adapter.user.dto.UserInfoResponse;
import com.raon.tikitaka.application.post.in.GetMyPostsUseCase;
import com.raon.tikitaka.application.user.in.GetMyInfoUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 내 정보 조회 API. 대상 유저는 토큰으로 식별하므로 별도 파라미터가 없다.
 * 랭킹에서 내 동네 강조, 마이페이지, 동네 변경 화면 등 프론트 전반이 쓴다.
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserInfoController {

    private final GetMyInfoUseCase getMyInfoUseCase;
    private final GetMyPostsUseCase getMyPostsUseCase;

    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> getMyInfo(@AuthenticationPrincipal UUID userId) {
        UserInfoResponse response = UserInfoResponse.from(getMyInfoUseCase.getMyInfo(userId));
        return ApiResponse.of(200, "내 정보 조회 성공", response);
    }

    /**
     * 마이페이지 "게시물 보관함"용. 삭제된 글은 제외하고 최신순으로 내려준다.
     * GET /api/post/** 는 SecurityConfig에서 비로그인 조회를 허용하므로 그 아래 두지 않고
     * /api/user/me 밑에 둬서 인증이 항상 걸리도록 한다.
     */
    @GetMapping("/me/posts")
    public ApiResponse<MyPostListResponse> getMyPosts(@AuthenticationPrincipal UUID userId) {
        MyPostListResponse response = MyPostListResponse.from(getMyPostsUseCase.getMyPosts(userId));
        return ApiResponse.of(200, "내 게시물 목록 조회 성공", response);
    }
}
