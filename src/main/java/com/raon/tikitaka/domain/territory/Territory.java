package com.raon.tikitaka.domain.territory;

import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 한 사이클 안의 땅 하나(동 × 사이클). 사이클이 끝나면 새 사이클에 새 행이 생긴다(초기화, 결정 #7).
 * 동시 점령은 @Version 낙관적 락으로 막는다. 통행료·판정 트랜잭션에서 나중에 커밋한 쪽이 실패한다.
 */
@Entity
@Table(name = "territory", uniqueConstraints =
        @UniqueConstraint(name = "uk_territory_cycle_dong", columnNames = {"cycle_id", "dong_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Territory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "territory_id")
    private Long territoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id", nullable = false)
    private TerritoryCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dong_id", nullable = false)
    private Dong dong;

    /** 동 대표. null이면 주인 없는 땅 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private Users owner;

    /** 주인의 팀(차지한 시점의 본진). 같은 팀 판별과 땅따먹기 랭킹 집계에 쓴다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_location_id")
    private Location ownerLocation;

    /** 미션 = 형용사 + 명사 키워드(각 1~10자, 한글만). 예: 파란 + 하늘 */
    @Column(name = "mission_adjective", length = 10)
    private String missionAdjective;

    @Column(name = "mission_noun", length = 10)
    private String missionNoun;

    /** 대표가 정한 채점 기준(자유 입력, 최대 50자). 채점 프롬프트에 데이터로만 들어간다 */
    @Column(name = "scoring_criteria", length = 50)
    private String scoringCriteria;

    /** 미션 변경 요청. 대표가 새 미션으로 다시 찍어야 적용된다 */
    @Column(name = "pending_adjective", length = 10)
    private String pendingAdjective;

    @Column(name = "pending_noun", length = 10)
    private String pendingNoun;

    @Column(name = "pending_criteria", length = 50)
    private String pendingCriteria;

    @Column(name = "defense_photo")
    private String defensePhoto;

    /** 대표 사진을 찍었을 때의 AI 점수. 감쇠 전 값 */
    @Column(name = "base_score")
    private Integer baseScore;

    /** 도전자가 넘어야 하는 점수. 감쇠되면 base_score보다 낮아진다 */
    @Column(name = "defense_score", nullable = false)
    private int defenseScore;

    /** 인기도로 정해지는 기본 땅값. 배치가 매일 갱신한다 */
    @Column(name = "base_land_price", nullable = false)
    private int baseLandPrice;

    /** 땅 개발로 쌓인 값 */
    @Column(name = "development_value", nullable = false)
    private int developmentValue;

    @Column(name = "owner_last_active_at")
    private LocalDateTime ownerLastActiveAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** 사이클 시작 시 동마다 주인 없는 땅으로 만든다 */
    public static Territory open(TerritoryCycle cycle, Dong dong, int baseLandPrice, int unownedMinScore) {
        Territory t = new Territory();
        t.cycle = cycle;
        t.dong = dong;
        t.baseLandPrice = baseLandPrice;
        t.defenseScore = unownedMinScore;
        return t;
    }

    // ---------- 주인 ----------

    /** 5일차 대표 자동 배치 */
    public void assign(Users representative, Location team, LocalDateTime now) {
        changeOwner(representative, team, now);
    }

    public boolean isOwned() {
        return owner != null;
    }

    public boolean isOwnedBy(UUID userId) {
        return owner != null && Objects.equals(owner.getUserId(), userId);
    }

    /** 같은 팀이면 도전할 수 없다(우리 팀 땅 공격 금지). 주인 없는 땅은 누구의 팀도 아니다 */
    public boolean isTeam(Long locationId) {
        return ownerLocation != null && Objects.equals(ownerLocation.getLocationId(), locationId);
    }

    /** 도전 대상인지(기간 제외): 내 땅도, 우리 팀 땅도 아니어야 한다. 주인 없는 땅은 누구나 도전할 수 있다 */
    public boolean isChallengeableBy(UUID userId, Long userLocationId) {
        return !isOwnedBy(userId) && !isTeam(userLocationId);
    }

    // ---------- 땅 세팅 / 방어 ----------

    /** 화면 표시용 미션 문장. 예: "파란 하늘" */
    public String getMission() {
        return missionAdjective == null ? null : missionAdjective + " " + missionNoun;
    }

    public boolean hasPendingMission() {
        return pendingAdjective != null;
    }

    /** 5~7일차 땅 세팅: 미션 + 대표 사진. 사진 점수가 첫 방어 점수가 된다. 기간 안에 다시 하면 덮어쓴다 */
    public void setup(String adjective, String noun, String criteria, String photoKey, int score, LocalDateTime now) {
        this.missionAdjective = adjective;
        this.missionNoun = noun;
        this.scoringCriteria = criteria;
        clearPending();
        updateDefense(photoKey, score, now);
    }

    /** 8일차: 세팅하지 않은 땅은 기본 미션 + 기준 점수로 시작한다. 대표 자리는 유지한다 */
    public void applyDefaultIfNotSetUp(String adjective, String noun, String criteria, int unownedMinScore) {
        if (missionAdjective != null) {
            return;
        }
        this.missionAdjective = adjective;
        this.missionNoun = noun;
        this.scoringCriteria = criteria;
        this.defenseScore = unownedMinScore;
    }

    public void requestMissionChange(String adjective, String noun, String criteria) {
        this.pendingAdjective = adjective;
        this.pendingNoun = noun;
        this.pendingCriteria = criteria;
    }

    /**
     * 대표 재촬영. 대기 중인 미션 변경이 있으면 이 사진으로 새 미션을 적용한다.
     * 점수는 마지막 촬영 점수로 교체한다(결정 #17 제안안: 최고점만 골라 가지는 것을 막는다).
     */
    public void retake(String photoKey, int score, LocalDateTime now) {
        if (hasPendingMission()) {
            this.missionAdjective = pendingAdjective;
            this.missionNoun = pendingNoun;
            this.scoringCriteria = pendingCriteria;
            clearPending();
        }
        updateDefense(photoKey, score, now);
    }

    /** 방치 감쇠. floor 아래로는 내리지 않는다 */
    public void decay(int amount, int floor) {
        this.defenseScore = Math.max(floor, defenseScore - amount);
    }

    public boolean isIdle(LocalDateTime now, int idleDays) {
        return owner != null && ownerLastActiveAt != null
                && !ownerLastActiveAt.plusDays(idleDays).isAfter(now);
    }

    // ---------- 도전 ----------

    /** 같거나 높으면 도전자 승 */
    public boolean isBeatenBy(int score) {
        return score >= defenseScore;
    }

    /**
     * 점령. 대표·대표 사진·방어 점수를 바꾸고, 미션은 새 대표가 바꿀 때까지 유지한다.
     * 이전 대표의 미션 변경 요청은 버린다. 개발 가치 처리(결정 #9)는 정해지기 전까지 유지한다.
     */
    public void capture(Users challenger, Location team, String photoKey, int score, LocalDateTime now) {
        changeOwner(challenger, team, now);
        clearPending();
        updateDefense(photoKey, score, now);
    }

    // ---------- 땅값 ----------

    /** 땅값 = 기본 땅값 + 개발 가치. 상한은 두지 않는다 */
    public int landPrice() {
        return baseLandPrice + developmentValue;
    }

    public void updateBaseLandPrice(int baseLandPrice) {
        this.baseLandPrice = baseLandPrice;
    }

    /** 개발 성공. 항상 같은 폭만큼 오른다 */
    public void develop(int gain) {
        this.developmentValue += gain;
    }

    private void clearPending() {
        this.pendingAdjective = null;
        this.pendingNoun = null;
        this.pendingCriteria = null;
    }

    private void changeOwner(Users user, Location team, LocalDateTime now) {
        this.owner = user;
        this.ownerLocation = team;
        this.ownerLastActiveAt = now;
    }

    private void updateDefense(String photoKey, int score, LocalDateTime now) {
        this.defensePhoto = photoKey;
        this.baseScore = score;
        this.defenseScore = score;
        this.ownerLastActiveAt = now;
    }
}
