package com.ligalytics.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "matches")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_date", nullable = false)
    private LocalDateTime matchDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @Column(name = "full_time_home_goals")
    private Integer fullTimeHomeGoals;

    @Column(name = "full_time_away_goals")
    private Integer fullTimeAwayGoals;

    @Column(name = "home_xg")
    private Double homeXg;

    @Column(name = "away_xg")
    private Double awayXg;

    @Column(name = "corners")
    private Integer corners;

    @Column(name = "yellow_cards")
    private Integer yellowCards;

    @Column(name = "red_cards")
    private Integer redCards;

    /** Tarjetas por equipo (football-data: HY, AY, HR, AR); nulas si la fuente no las aporta. */
    @Column(name = "home_yellow_cards")
    private Integer homeYellowCards;

    @Column(name = "away_yellow_cards")
    private Integer awayYellowCards;

    @Column(name = "home_red_cards")
    private Integer homeRedCards;

    @Column(name = "away_red_cards")
    private Integer awayRedCards;

    /** Tiros y faltas del partido (football-data: HS, AS, HST, AST, HF, AF). */
    @Column(name = "home_shots")
    private Integer homeShots;

    @Column(name = "away_shots")
    private Integer awayShots;

    @Column(name = "home_shots_on_target")
    private Integer homeShotsOnTarget;

    @Column(name = "away_shots_on_target")
    private Integer awayShotsOnTarget;

    @Column(name = "home_fouls")
    private Integer homeFouls;

    @Column(name = "away_fouls")
    private Integer awayFouls;
}
