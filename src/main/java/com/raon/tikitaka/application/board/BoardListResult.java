package com.raon.tikitaka.application.board;

import com.raon.tikitaka.domain.board.Board;

import java.util.List;

/**
 * 게시판 목록 조회 결과. 현재 라운드의 게시판 전체와 호출자의 메인 동네 ID를 담는다.
 * myLocationId는 응답에서 내 동네 매치 여부를 표시하는 데 쓴다.
 */
public record BoardListResult(List<Board> boards, Long myLocationId) {
}
