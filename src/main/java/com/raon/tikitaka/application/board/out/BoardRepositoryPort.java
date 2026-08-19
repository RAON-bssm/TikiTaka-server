package com.raon.tikitaka.application.board.out;

import com.raon.tikitaka.domain.board.Board;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BoardRepositoryPort {

    List<Board> findAllActiveBoards(LocalDateTime now, Long locationId);

    Optional<Board> findActiveById(Long boardId, LocalDateTime now, Long locationId);

    Board save(Board board);
}
