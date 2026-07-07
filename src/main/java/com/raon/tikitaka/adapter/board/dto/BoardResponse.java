package com.raon.tikitaka.adapter.board.dto;

import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.match.Match;

public record BoardResponse(
        Long boardId,
        String team1Name,
        String team2Name,
        String mission,
        String matchType,
        Integer season,
        Integer round
) {

    public static BoardResponse from(Board board) {
        Match match = board.getMatch();
        return new BoardResponse(
                board.getBoardId(),
                match.getTeam1().getLocationName(),
                match.getTeam2().getLocationName(),
                match.getMission(),
                match.getMatchType().getDescription(),
                match.getStage().getSeason(),
                match.getStage().getRound()
        );
    }
}
