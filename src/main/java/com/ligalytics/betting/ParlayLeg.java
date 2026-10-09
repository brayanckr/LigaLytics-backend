package com.ligalytics.betting;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una selección de una combinada, con la cuota fijada por el servidor al apostar. */
@Entity
@Table(name = "parlay_legs")
@Getter
@Setter
@NoArgsConstructor
public class ParlayLeg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parlay_id", nullable = false)
    private Parlay parlay;

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

    @Column(name = "odds_source", nullable = false, length = 12)
    private String oddsSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BetStatus status = BetStatus.PENDING;
}
