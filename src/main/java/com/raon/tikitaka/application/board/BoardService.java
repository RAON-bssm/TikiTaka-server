package com.raon.tikitaka.application.board;

import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.application.board.out.BoardRepositoryPort;
import com.raon.tikitaka.domain.board.Board;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService implements GetBoardUseCase {

    private final BoardRepositoryPort boardRepositoryPort;

    @Override
    public List<Board> getBoards() {
        return boardRepositoryPort.findAllActiveBoards();
    }

    @Override
    public String getMission(Long boardId) {
        Board board = boardRepositoryPort.findActiveById(boardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시판을 찾을 수 없습니다."));
        return board.getMatch().getMission();
    }
}
