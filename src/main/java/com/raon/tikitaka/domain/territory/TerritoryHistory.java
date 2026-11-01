package com.raon.tikitaka.domain.territory;

import com.raon.tikitaka.domain.enums.TerritoryEventType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** 땅 주인 변경 이력 */
@Entity
@Table(name = "territory_history", indexes = @Index(name = "idx_territory_history_territory", columnList = "territory_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TerritoryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "territory_history_id")
    private Long id;

    @Column(name = "territory_id", nullable = false)
    private Long territoryId;

    @Column(name = "from_owner_id", columnDefinition = "uuid")
    private UUID fromOwnerId;

    @Column(name = "to_owner_id", nullable = false, columnDefinition = "uuid")
    private UUID toOwnerId;

    @Column(name = "score")
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private TerritoryEventType reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static TerritoryHistory of(Long territoryId, UUID fromOwnerId, UUID toOwnerId, Integer score,
                                      TerritoryEventType reason, LocalDateTime now) {
        TerritoryHistory h = new TerritoryHistory();
        h.territoryId = territoryId;
        h.fromOwnerId = fromOwnerId;
        h.toOwnerId = toOwnerId;
        h.score = score;
        h.reason = reason;
        h.createdAt = now;
        return h;
    }
}
