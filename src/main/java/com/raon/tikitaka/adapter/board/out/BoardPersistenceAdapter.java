package com.raon.tikitaka.adapter.board.out;

import com.raon.tikitaka.application.board.out.BoardRepositoryPort;
import com.raon.tikitaka.domain.board.Board;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BoardPersistenceAdapter implements BoardRepositoryPort {

    private final BoardJpaRepository boardJpaRepository;

    @Override
    public List<Board> findAllActiveBoards() {
        return boardJpaRepository.findAllActiveWithMatch();
    }

    @Override
    public Optional<Board> findActiveById(Long boardId) {
        return boardJpaRepository.findActiveByIdWithMatch(boardId);
    }
}
