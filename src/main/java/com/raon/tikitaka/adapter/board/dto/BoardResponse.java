package com.raon.tikitaka.adapter.board.dto;

import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.match.Match;

/**
 * 게시판 목록의 한 건. myMatch가 true면 내 메인 동네가 참가한 매치의 게시판이다.
 */
public record BoardResponse(
        Long boardId,
        String team1Name,
        String team2Name,
        String mission,
        String matchType,
        Integer season,
        Integer round,
        boolean myMatch
) {

    public static BoardResponse from(Board board, boolean myMatch) {
        Match match = board.getMatch();
        return new BoardResponse(
                board.getBoardId(),
                match.getTeam1().getLocationName(),
                match.getTeam2().getLocationName(),
                match.getMission(),
                match.getMatchType().getDescription(),
                match.getStage().getSeason(),
                match.getStage().getRound(),
                myMatch
        );
    }
}
