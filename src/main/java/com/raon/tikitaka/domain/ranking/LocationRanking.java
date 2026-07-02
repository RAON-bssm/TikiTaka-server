package com.raon.tikitaka.domain.ranking;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "location_ranking", indexes = {
        @Index(name = "idx_location_ranking", columnList = "location_rank")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationRanking {

    @Id
    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "location_rank")
    private Integer locationRank;

    @Column(name = "location_score")
    private Integer locationScore;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
