package com.ligalytics.betting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Apuesta combinada (parlay) de demostración: varias selecciones de partidos distintos con una sola cuota, que es
 * el producto de las cuotas de cada una. Se gana solo si aciertan todas; una selección anulada cuenta con cuota 1.
 */
@Entity
@Table(name = "parlays")
@Getter
@Setter
@NoArgsConstructor
public class Parlay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_odds", nullable = false)
    private double totalOdds;

    @Column(nullable = false)
    private long stake;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BetStatus status = BetStatus.PENDING;

    /** Dinero que vuelve al saldo al liquidar (0 si se pierde; el importe si todas las selecciones se anulan). */
    @Column(nullable = false)
    private long payout;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt = Instant.now();

    @Column(name = "settled_at")
    private Instant settledAt;

    @OneToMany(mappedBy = "parlay", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<ParlayLeg> legs = new ArrayList<>();

    public void addLeg(ParlayLeg leg) {
        leg.setParlay(this);
        legs.add(leg);
    }
}
