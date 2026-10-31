package com.raon.tikitaka.domain.territory;

import com.raon.tikitaka.domain.enums.AssignmentType;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사이클별 동 대표 선발 결과. 선발 시점의 본진(location)을 스냅샷으로 남긴다 */
@Entity
@Table(name = "representative", uniqueConstraints =
        @UniqueConstraint(name = "uk_representative_cycle_user", columnNames = {"cycle_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Representative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "representative_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id", nullable = false)
    private TerritoryCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    /** 구 안에서의 대표 순위(1부터) */
    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "score", nullable = false)
    private long score;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_dong_id", nullable = false)
    private Dong assignedDong;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_by", nullable = false)
    private AssignmentType assignedBy;

    public static Representative of(TerritoryCycle cycle, Users user, Location location, int rank, long score,
                                    Dong assignedDong, AssignmentType assignedBy) {
        Representative r = new Representative();
        r.cycle = cycle;
        r.user = user;
        r.location = location;
        r.rank = rank;
        r.score = score;
        r.assignedDong = assignedDong;
        r.assignedBy = assignedBy;
        return r;
    }
}
