package com.raon.tikitaka.domain.territory;

import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 땅 개발 이력. 확률과 난수(roll)를 남겨 결과를 검증할 수 있게 한다 */
@Entity
@Table(name = "land_development", indexes =
        @Index(name = "idx_land_dev_user_created", columnList = "user_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandDevelopment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "land_development_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "territory_id", nullable = false)
    private Territory territory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "cost", nullable = false)
    private int cost;

    @Column(name = "probability", nullable = false)
    private double probability;

    @Column(name = "roll", nullable = false)
    private double roll;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "value_added", nullable = false)
    private int valueAdded;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static LandDevelopment record(Territory territory, Users user, int cost, double probability,
                                         double roll, boolean success, int valueAdded, LocalDateTime now) {
        LandDevelopment d = new LandDevelopment();
        d.territory = territory;
        d.user = user;
        d.cost = cost;
        d.probability = probability;
        d.roll = roll;
        d.success = success;
        d.valueAdded = valueAdded;
        d.createdAt = now;
        return d;
    }
}
