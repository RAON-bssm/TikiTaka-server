package com.raon.tikitaka.domain.territory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 금칙어 뼈대 비교의 오탐 예외(예: 시발점, 시바견). 이 단어는 검사 전에 지운다 */
@Entity
@Table(name = "allowed_word")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AllowedWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allowed_word_id")
    private Long id;

    @Column(name = "word", nullable = false, unique = true, length = 30)
    private String word;
}
