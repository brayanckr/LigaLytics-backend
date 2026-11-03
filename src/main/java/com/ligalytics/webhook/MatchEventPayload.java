package com.ligalytics.webhook;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/** Evento en vivo de un partido tal como lo envía el proveedor de datos. Los campos desconocidos se ignoran. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Evento en vivo de un partido (gol, tarjeta, córner, inicio, final...)")
public record MatchEventPayload(
        @Schema(description = "Identificador del partido en el proveedor") String eventId,
        @Schema(description = "Tipo de evento: GOAL, YELLOW_CARD, RED_CARD, CORNER, KICK_OFF, FULL_TIME...") String type,
        String homeTeam,
        String awayTeam,
        @Schema(description = "Minuto de juego") Integer minute,
        Integer homeScore,
        Integer awayScore,
        @Schema(description = "Momento en que ocurrió el evento") Instant occurredAt) {

    /** Un evento es utilizable si identifica el partido y dice qué pasó. */
    public boolean isValid() {
        return eventId != null && !eventId.isBlank() && type != null && !type.isBlank();
    }
}
