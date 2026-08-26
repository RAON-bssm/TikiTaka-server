package com.raon.tikitaka.domain.match;


import com.raon.tikitaka.domain.enums.MatchType;
import com.raon.tikitaka.domain.location.Location;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "match",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_match_stage_team1", columnNames = {"stage_id", "team1_id"}),
                @UniqueConstraint(name = "uk_match_stage_team2", columnNames = {"stage_id", "team2_id"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Long matchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team1_id", nullable = false)
    private Location team1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team2_id", nullable = false)
    private Location team2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "win_team")
    private Location winTeam;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false)
    @ColumnDefault("'NORMAL'")
    private MatchType matchType = MatchType.NORMAL;

    /**
     * 매칭 시점 각 팀의 ACTIVE 인원수 스냅샷. 라운드 중 인원 변동에 영향받지 않는다.
     */
    @Column(name = "team1_member_count", nullable = false)
    private Integer team1MemberCount;

    @Column(name = "team2_member_count", nullable = false)
    private Integer team2MemberCount;

    @Column(name = "mission", nullable = false)
    private String mission;

    /**
     * 부전승 매치는 team1과 team2를 같은 동네로 만든다. winTeam은 null로 시작한다.
     */
    public static Match create(Stage stage, Location team1, Location team2, MatchType matchType,
                               int team1MemberCount, int team2MemberCount, String mission) {
        Match match = new Match();
        match.stage = stage;
        match.team1 = team1;
        match.team2 = team2;
        match.matchType = matchType;
        match.team1MemberCount = team1MemberCount;
        match.team2MemberCount = team2MemberCount;
        match.mission = mission;
        return match;
    }

}
