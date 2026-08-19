package com.raon.tikitaka.domain.board;

import com.raon.tikitaka.domain.match.Match;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "board")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "board_id")
    private Long boardId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false, unique = true)
    private Match match;

    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive;

    /**
     * isActive를 반드시 true로 명시한다. primitive boolean이라 명시하지 않으면
     * Hibernate가 false를 넣어 게시판이 목록에서 전부 사라진다.
     */
    public static Board create(Match match) {
        Board board = new Board();
        board.match = match;
        board.isActive = true;
        return board;
    }

}
