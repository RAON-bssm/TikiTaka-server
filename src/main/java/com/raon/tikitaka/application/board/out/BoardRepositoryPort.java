package com.raon.tikitaka.application.board.out;

import com.raon.tikitaka.domain.board.Board;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BoardRepositoryPort {

    /**
     * 현재 라운드의 게시판 전체 목록. 동네 제한 없이 모든 매치의 게시판을 돌려준다.
     */
    List<Board> findAllActiveBoards(LocalDateTime now);

    /**
     * 게시물 작성 경로의 단건 조회. 내 동네가 참가한 매치의 게시판만 찾아지므로
     * 남의 동네 게시판이나 종료된 라운드의 게시판에는 글을 쓸 수 없다.
     */
    Optional<Board> findActiveById(Long boardId, LocalDateTime now, Long locationId);

    Board save(Board board);
}
