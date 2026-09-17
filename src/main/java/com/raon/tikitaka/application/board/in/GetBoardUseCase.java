package com.raon.tikitaka.application.board.in;

import com.raon.tikitaka.application.board.BoardListResult;

import java.util.UUID;

public interface GetBoardUseCase {

    /**
     * 현재 라운드의 게시판 전체 목록과 호출자가 지금 있는 동네 ID를 돌려준다.
     * 모든 동네 경기를 구경할 수 있고 내 매치 표시는 응답 계층이 담당한다.
     */
    BoardListResult getBoards(UUID userId);

    /**
     * 게시물 작성 경로의 미션 조회. 지금 있는 동네가 참가한 매치의 게시판만 찾아지므로
     * 글은 현재 지역 게시판에만 쓸 수 있다.
     */
    String getMission(Long boardId, UUID userId);
}
