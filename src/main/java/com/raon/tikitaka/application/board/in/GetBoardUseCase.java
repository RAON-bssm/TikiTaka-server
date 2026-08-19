package com.raon.tikitaka.application.board.in;

import com.raon.tikitaka.application.board.BoardListResult;

import java.util.UUID;

public interface GetBoardUseCase {

    /**
     * 현재 라운드의 게시판 전체 목록과 호출자의 메인 동네 ID를 돌려준다.
     * 다른 동네 경기도 구경할 수 있고 내 동네 매치 표시는 응답 계층이 담당한다.
     */
    BoardListResult getBoards(UUID userId);

    String getMission(Long boardId, UUID userId);
}
