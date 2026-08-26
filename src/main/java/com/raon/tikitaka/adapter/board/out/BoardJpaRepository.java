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
     * 현재 라운드의 게시판 전체 목록. 다른 동네 경기도 구경할 수 있게 동네 조건을 걸지 않는다.
     * 작성 검증은 아래 단건 조회가 담당하므로 글은 여전히 내 동네 게시판에만 쓸 수 있다.
     * 라운드 전환 시 실행되는 코드는 없고 now가 지나면 조회 결과가 자연히 바뀐다.
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
            order by b.boardId
            """)
    List<Board> findAllActiveWithMatch(@Param("now") LocalDateTime now);

    /**
     * 단건 조회. 게시물 작성 시 미션을 가져오는 경로라 목록과 같은 필터가 필요하다.
     * 필터가 없으면 종료된 라운드의 게시판 ID로 계속 글을 쓸 수 있다.
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
     * 점수 계산과 차감에 쓰는 조회. 매치와 팀, 라운드까지 즉시 로딩한다.
     * 검증이 끝난 뒤 호출되는 경로라 시간과 동네 조건은 걸지 않는다.
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
