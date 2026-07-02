package com.raon.tikitaka.domain.match;


import com.raon.tikitaka.domain.enums.MatchType;
import com.raon.tikitaka.domain.location.Location;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "match")
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

    @Column(name = "match_type")
    private MatchType matchType;

    @Column(name = "mission", nullable = false)
    private String mission;

}
