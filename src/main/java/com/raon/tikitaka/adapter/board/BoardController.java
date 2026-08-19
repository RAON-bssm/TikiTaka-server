package com.raon.tikitaka.adapter.board;

import com.raon.tikitaka.adapter.board.dto.BoardListResponse;
import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
public class BoardController {

    private final GetBoardUseCase getBoardUseCase;

    /**
     * 현재 라운드의 게시판 전체 목록. 다른 동네 경기도 구경할 수 있고
     * 내 동네 매치는 myMatch가 true로 내려가며 목록 맨 앞에 온다.
     * 인증은 JwtAuthenticationFilter가 처리하고 검증된 userId가 principal로 주입된다.
     */
    @GetMapping
    public ApiResponse<BoardListResponse> getBoards(@AuthenticationPrincipal UUID userId) {
        BoardListResponse response = BoardListResponse.from(getBoardUseCase.getBoards(userId));
        return ApiResponse.of(200, "게시판 목록 조회 성공", response);
    }
}
