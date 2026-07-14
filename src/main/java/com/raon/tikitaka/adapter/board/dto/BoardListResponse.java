package com.raon.tikitaka.adapter.board.dto;

import com.raon.tikitaka.domain.board.Board;

import java.util.List;

public record BoardListResponse(List<BoardResponse> board) {

    public static BoardListResponse from(List<Board> boards) {
        return new BoardListResponse(boards.stream().map(BoardResponse::from).toList());
    }
}
