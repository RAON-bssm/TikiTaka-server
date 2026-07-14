package com.raon.tikitaka.adapter.board;

import com.raon.tikitaka.adapter.board.dto.BoardListResponse;
import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
public class BoardController {

    private final GetBoardUseCase getBoardUseCase;

    @GetMapping
    public ApiResponse<BoardListResponse> getBoards() {
        BoardListResponse response = BoardListResponse.from(getBoardUseCase.getBoards());
        return ApiResponse.of(200, "게시판 목록 조회 성공", response);
    }
}
