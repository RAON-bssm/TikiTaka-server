package com.raon.tikitaka.adapter.board.out;

import com.raon.tikitaka.domain.board.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BoardJpaRepository extends JpaRepository<Board, Long> {

    /**
     * 게시판 목록 — "현재 라운드 + 내 동네가 참가한 매치"만.
     * 라운드 전환 시 실행되는 코드는 없고, now가 넘어가면 조회 결과가 자연히 바뀐다.
     */
    @Query("""
            select b from Board b
            join fetch b.match m
            join fetch m.team1 t1
            join fetch m.team2 t2
            join fetch m.stage s
            where b.isActive = true
              and s.startedAt <= :now
              and :now < s.endedAt
              and (t1.locationId = :locationId or t2.locationId = :locationId)
            """)
    List<Board> findAllActiveWithMatch(@Param("now") LocalDateTime now,
                                       @Param("locationId") Long locationId);

    /**
     * 단건 조회 — 게시물 작성 시 미션을 가져오는 경로라서 같은 필터가 반드시 필요하다.
     * 없으면 종료된 라운드의 게시판 ID로 계속 글을 쓸 수 있다 (23:55 열고 00:01 제출하는 타이밍 사고).
     */
    @Query("""
            select b from Board b
            join fetch b.match m
            join fetch m.team1 t1
            join fetch m.team2 t2
            join fetch m.stage s
            where b.boardId = :boardId
              and b.isActive = true
              and s.startedAt <= :now
              and :now < s.endedAt
              and (t1.locationId = :locationId or t2.locationId = :locationId)
            """)
    Optional<Board> findActiveByIdWithMatch(@Param("boardId") Long boardId,
                                            @Param("now") LocalDateTime now,
                                            @Param("locationId") Long locationId);

    /**
     * 필터 없이 매치 그래프(팀·라운드)까지 즉시 로딩 — 점수 계산·차감(8-2)용.
     * 위 필터 쿼리와 달리 시간·동네 조건이 없다 (검증은 서비스에서 이미 끝난 뒤 호출되므로).
     */
    @Query("""
            select b from Board b
            join fetch b.match m
            join fetch m.team1
            join fetch m.team2
            join fetch m.stage
            where b.boardId = :boardId
            """)
    Optional<Board> findByIdWithMatchGraph(@Param("boardId") Long boardId);
}
