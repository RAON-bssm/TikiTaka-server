package com.raon.tikitaka.domain.territory;

import com.raon.tikitaka.domain.enums.ChallengeResult;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 채점까지 끝난 도전 한 건. 통행료 차감·판정과 같은 트랜잭션에서 저장되므로,
 * 서버 거절이나 S3·AI 오류로 끝난 요청은 남지 않는다.
 */
@Entity
@Table(name = "challenge", indexes = {
        @Index(name = "idx_challenge_territory_date", columnList = "territory_id, attack_date"),
        @Index(name = "idx_challenge_challenger", columnList = "challenger_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "challenge_id", columnDefinition = "uuid")
    private UUID challengeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "territory_id", nullable = false)
    private Territory territory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenger_id", nullable = false)
    private Users challenger;

    /** 도전 시점의 본진(팀) 스냅샷 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenger_location_id", nullable = false)
    private Location challengerLocation;

    @Column(name = "photo_key", nullable = false)
    private String photoKey;

    @Column(name = "photo_hash", nullable = false, length = 64)
    private String photoHash;

    /** 촬영 좌표. 기록만 하고 검증하지 않는다(결정 #4 모니터링용) */
    private Double lat;
    private Double lng;

    @Column(name = "score", nullable = false)
    private int score;

    /** 판정에 쓴 방어 점수 */
    @Column(name = "defense_score_at", nullable = false)
    private int defenseScoreAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private ChallengeResult result;

    @Column(name = "toll", nullable = false)
    private int toll;

    /** 대표에게 입금한 몫. 주인 없는 땅이면 0 */
    @Column(name = "owner_share", nullable = false)
    private int ownerShare;

    @Column(name = "ai_review", columnDefinition = "text")
    private String aiReview;

    /** 인기도 집계용 날짜(KST). (공격자, 날짜) 단위로 센다 */
    @Column(name = "attack_date", nullable = false)
    private LocalDate attackDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Challenge record(Territory territory, Users challenger, Location challengerLocation,
                                   String photoKey, String photoHash, Double lat, Double lng,
                                   int score, int defenseScoreAt, ChallengeResult result,
                                   int toll, int ownerShare, String aiReview, LocalDateTime now) {
        Challenge c = new Challenge();
        c.territory = territory;
        c.challenger = challenger;
        c.challengerLocation = challengerLocation;
        c.photoKey = photoKey;
        c.photoHash = photoHash;
        c.lat = lat;
        c.lng = lng;
        c.score = score;
        c.defenseScoreAt = defenseScoreAt;
        c.result = result;
        c.toll = toll;
        c.ownerShare = ownerShare;
        c.aiReview = aiReview;
        c.attackDate = now.toLocalDate();
        c.createdAt = now;
        return c;
    }
}
