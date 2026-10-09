package com.ligalytics.betting;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Apuesta de demostración: se paga con saldo ficticio y la cuota queda fijada cuando se hace. */
@Entity
@Table(name = "bets")
@Getter
@Setter
@NoArgsConstructor
public class Bet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private long eventId;

    @Column(name = "home_team", nullable = false, length = 120)
    private String homeTeam;

    @Column(name = "away_team", nullable = false, length = 120)
    private String awayTeam;

    @Column(nullable = false)
    private Instant kickoff;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Market market;

    @Column(nullable = false, length = 8)
    private String selection;

    @Column
    private Double line;

    @Column(nullable = false)
    private double odds;

    /** Origen de la cuota: consenso (casas reales) o demo (calculada por el modelo). */
    @Column(name = "odds_source", nullable = false, length = 12)
    private String oddsSource;

    @Column(nullable = false)
    private long stake;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BetStatus status = BetStatus.PENDING;

    /** Dinero que vuelve al saldo al liquidar (0 si se pierde; el importe si se anula). */
    @Column(nullable = false)
    private long payout;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt = Instant.now();

    @Column(name = "settled_at")
    private Instant settledAt;
}
