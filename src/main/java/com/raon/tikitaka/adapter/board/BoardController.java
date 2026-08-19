package com.raon.tikitaka.adapter.board;

import com.raon.tikitaka.adapter.board.dto.BoardListResponse;
import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
public class BoardController {

    private final GetBoardUseCase getBoardUseCase;

    /**
     * "내 동네가 참가한 현재 라운드의 게시판"만 내려주므로 인증 헤더가 필요해졌다.
     * ⚠️ 프론트 파급: 기존에는 헤더 없이 호출 가능했음 — 비로그인 호출 시 401.
     * required = false: 헤더가 없으면 스프링이 메서드 진입 전에 400을 던져버리므로,
     * 일단 들여보내고 resolveUserId의 null 체크가 401을 주도록 한다.
     */
    @GetMapping
    public ApiResponse<BoardListResponse> getBoards(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UUID userId = resolveUserId(authorization);
        BoardListResponse response = BoardListResponse.from(getBoardUseCase.getBoards(userId));
        return ApiResponse.of(200, "게시판 목록 조회 성공", response);
    }

    // PostController와 중복 — 정식 인증(로그인 브랜치) 도입 시 공용 유틸/ArgumentResolver로 추출할 것
    private UUID resolveUserId(String authorization) {
        if (authorization == null || authorization.isBlank() || authorization.equalsIgnoreCase("null")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        }
    }
}
