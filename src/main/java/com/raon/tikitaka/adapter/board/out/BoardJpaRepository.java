package com.raon.tikitaka.adapter.board.out;

import com.raon.tikitaka.domain.board.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BoardJpaRepository extends JpaRepository<Board, Long> {

    @Query("""
            select b from Board b
            join fetch b.match m
            join fetch m.team1
            join fetch m.team2
            join fetch m.stage
            where b.isActive = true
            """)
    List<Board> findAllActiveWithMatch();
}
