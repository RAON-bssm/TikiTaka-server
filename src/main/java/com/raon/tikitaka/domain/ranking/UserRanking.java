package com.raon.tikitaka.domain.ranking;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_ranking", indexes = {
        @Index(name = "idx_user_ranking", columnList = "user_rank")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRanking {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "user_rank")
    private Integer userRank;

    @Column(name = "user_score")
    private Integer userScore;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
