package com.raon.tikitaka.domain.territory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 운영진 기본 미션. 땅따먹기 주간 시작 때 미션이 없는 땅(주인 없는 땅, 세팅 안 한 땅)에 랜덤으로 넣는다 */
@Entity
@Table(name = "default_mission", uniqueConstraints =
        @UniqueConstraint(name = "uk_default_mission", columnNames = {"adjective", "noun"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefaultMission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "default_mission_id")
    private Long id;

    @Column(name = "adjective", nullable = false, length = 10)
    private String adjective;

    @Column(name = "noun", nullable = false, length = 10)
    private String noun;

    @Column(name = "scoring_criteria", length = 50)
    private String scoringCriteria;
}
