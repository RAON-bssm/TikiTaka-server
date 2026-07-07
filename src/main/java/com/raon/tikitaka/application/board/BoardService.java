package com.raon.tikitaka.application.board;

import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.application.board.out.BoardRepositoryPort;
import com.raon.tikitaka.domain.board.Board;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
