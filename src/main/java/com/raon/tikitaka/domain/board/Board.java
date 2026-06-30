package com.raon.tikitaka.domain.board;


import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "board")
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long board_id;

    //@JoinColumn(name = "match_id", nullable = false)
    //private Match match;   match 불러와야하는데 아직 안만들어서 보류

    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean is_active;
}
