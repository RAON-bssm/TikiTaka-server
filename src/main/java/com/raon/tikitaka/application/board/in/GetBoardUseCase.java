package com.raon.tikitaka.application.board.in;

import com.raon.tikitaka.domain.board.Board;

import java.util.List;
import java.util.UUID;

public interface GetBoardUseCase {

    List<Board> getBoards(UUID userId);

    String getMission(Long boardId, UUID userId);
}
