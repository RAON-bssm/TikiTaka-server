package com.raon.tikitaka.application.board.in;

import com.raon.tikitaka.domain.board.Board;

import java.util.List;

public interface GetBoardUseCase {

    List<Board> getBoards();
}
