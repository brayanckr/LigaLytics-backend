package com.ligalytics.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Estadísticas reales de un partido terminado tal como las entregó API-Football, guardadas una sola vez: las de un
 * partido terminado no cambian, así que no se vuelven a pedir (cada petición gasta cuota del plan gratuito).
 */
@Entity
@Table(name = "match_api_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MatchApiStats {

    /** Id del partido en la tabla matches. */
    @Id
    @Column(name = "match_id")
    private Long matchId;

    /** Id del partido en API-Football. */
    @Column(name = "fixture_id", nullable = false)
    private Long fixtureId;

    /** Lista de estadísticas (clave, etiqueta, local, visitante) en JSON. */
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
