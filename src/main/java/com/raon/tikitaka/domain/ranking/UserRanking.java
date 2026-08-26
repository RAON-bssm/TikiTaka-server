package com.raon.tikitaka.domain.ranking;

import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 라운드별 개인 점수. 게시물이 올라올 때마다 실시간으로 누적된다.
 * 개인 점수는 round(AI점수 * K)로 팀 규모 조정치를 곱하지 않는다.
 */
@Entity
@Table(name = "user_ranking",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_ranking_stage_user",
                columnNames = {"stage_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_ranking_id")
    private Long userRankingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "user_score", nullable = false)
    private Long userScore;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
