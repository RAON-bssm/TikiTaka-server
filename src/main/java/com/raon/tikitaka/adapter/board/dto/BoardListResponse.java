package com.raon.tikitaka.adapter.board.dto;

import com.raon.tikitaka.application.board.BoardListResult;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.match.Match;

import java.util.ArrayList;
import java.util.List;

/**
 * 게시판 목록 응답. 내 동네 매치를 앞에 두고 나머지는 boardId 순으로 내려준다.
 */
public record BoardListResponse(List<BoardResponse> board) {

    public static BoardListResponse from(BoardListResult result) {
        List<BoardResponse> mine = new ArrayList<>();
        List<BoardResponse> others = new ArrayList<>();
        for (Board board : result.boards()) {
            boolean myMatch = isMyMatch(board, result.myLocationId());
            if (myMatch) {
                mine.add(BoardResponse.from(board, true));
            } else {
                others.add(BoardResponse.from(board, false));
            }
        }
        mine.addAll(others);
        return new BoardListResponse(mine);
    }

    private static boolean isMyMatch(Board board, Long myLocationId) {
        Match match = board.getMatch();
        return match.getTeam1().getLocationId().equals(myLocationId)
                || match.getTeam2().getLocationId().equals(myLocationId);
    }
}
