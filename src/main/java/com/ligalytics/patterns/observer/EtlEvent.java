package com.ligalytics.patterns.observer;

import java.time.Instant;
import java.util.Set;

/**
 * Evento publicado por el sujeto ETL al finalizar una ingesta. Transporta el
 * resumen del proceso y los equipos afectados para que los observadores puedan
 * reaccionar (reentrenar modelos, recalcular estadísticas, invalidar cachés...).
 *
 * @param source     fuente de datos (football-data, fbref, understat...)
 * @param reference  referencia lógica de la ingesta (división-temporada, ruta...)
 * @param season     temporada asociada, si se conoce
 * @param teamIds    identificadores de los equipos afectados
 * @param created    partidos creados
 * @param updated    partidos/registros actualizados
 * @param skipped    registros omitidos
 * @param location   URL o ruta de origen
 * @param occurredAt instante en que se completó la ingesta
 */
public record EtlEvent(
        String source,
        String reference,
        String season,
        Set<Long> teamIds,
        int created,
        int updated,
        int skipped,
        String location,
        Instant occurredAt
) {

    public EtlEvent {
        teamIds = teamIds == null ? Set.of() : Set.copyOf(teamIds);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static EtlEvent of(String source,
            String reference,
            String season,
            Set<Long> teamIds,
            int created,
            int updated,
            int skipped,
            String location) {
        return new EtlEvent(source, reference, season, teamIds, created, updated, skipped, location, Instant.now());
    }

    public boolean hasChanges() {
        return created + updated > 0;
    }
}
