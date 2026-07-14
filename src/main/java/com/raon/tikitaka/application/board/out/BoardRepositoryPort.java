package com.raon.tikitaka.application.board.out;

import com.raon.tikitaka.domain.board.Board;

import java.util.List;
import java.util.Optional;

public interface BoardRepositoryPort {

    List<Board> findAllActiveBoards();

    Optional<Board> findActiveById(Long boardId);
}
