package com.raon.tikitaka.application.board.out;

import com.raon.tikitaka.domain.board.Board;

import java.util.List;

public interface BoardRepositoryPort {

    List<Board> findAllActiveBoards();
}
