package com.raon.tikitaka.domain.territory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 욕설·비하 금칙어 원형. 서버가 발음 뼈대로 바꿔 변형까지 막는다 */
@Entity
@Table(name = "banned_word")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BannedWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "banned_word_id")
    private Long id;

    @Column(name = "word", nullable = false, unique = true, length = 30)
    private String word;
}
