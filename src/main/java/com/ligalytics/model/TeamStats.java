package com.ligalytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "team_stats",
        uniqueConstraints = @UniqueConstraint(name = "uk_team_stats_season", columnNames = { "team_id", "season" })
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 20)
    private String season;

    @Column(name = "matches_played")
    private Integer matchesPlayed;

    @Column(name = "wins")
    private Integer wins;

    @Column(name = "draws")
    private Integer draws;

    @Column(name = "losses")
    private Integer losses;

    @Column(name = "goals_for")
    private Integer goalsFor;

    @Column(name = "goals_against")
    private Integer goalsAgainst;

    @Column(name = "points")
    private Integer points;

    @Column(name = "average_goals_for")
    private Double averageGoalsFor;

    @Column(name = "average_goals_against")
    private Double averageGoalsAgainst;

    @Column(name = "average_corners")
    private Double averageCorners;

    @Column(name = "average_yellow_cards")
    private Double averageYellowCards;
}
