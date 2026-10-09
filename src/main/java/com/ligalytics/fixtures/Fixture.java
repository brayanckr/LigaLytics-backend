package com.ligalytics.fixtures;

import java.time.Instant;

/**
 * Partido del calendario, independiente del proveedor de datos.
 *
 * @param status SCHEDULED (por jugar), LIVE (en juego), FINISHED u OTHER (aplazado, suspendido...)
 */
public record Fixture(
        String id,
        Instant utcDate,
        String status,
        Integer matchday,
        String homeName,
        String awayName,
        String homeCrest,
        String awayCrest,
        Integer homeGoals,
        Integer awayGoals) {
}
