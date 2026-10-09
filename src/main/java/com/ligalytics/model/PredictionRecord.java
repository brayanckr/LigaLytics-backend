package com.ligalytics.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Predicción generada por el sistema, guardada en PostgreSQL para tener
 * trazabilidad (qué se predijo, cuándo y con qué probabilidades).
 */
@Entity
@Table(name = "predictions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "home_team_id", nullable = false)
    private Long homeTeamId;

    @Column(name = "away_team_id", nullable = false)
    private Long awayTeamId;

    @Column(name = "home_team", nullable = false, length = 120)
    private String homeTeam;

    @Column(name = "away_team", nullable = false, length = 120)
    private String awayTeam;

    @Column(name = "predicted_outcome", length = 20)
    private String predictedOutcome;

    @Column(name = "home_win_probability")
    private Double homeWinProbability;

    @Column(name = "draw_probability")
    private Double drawProbability;

    @Column(name = "away_win_probability")
    private Double awayWinProbability;

    @Column(name = "expected_home_goals")
    private Double expectedHomeGoals;

    @Column(name = "expected_away_goals")
    private Double expectedAwayGoals;

    @Column(name = "expected_corners")
    private Double expectedCorners;

    @Column(name = "expected_cards")
    private Double expectedCards;
}
