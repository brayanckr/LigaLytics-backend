package com.ligalytics.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Entrada de la tabla de posiciones (ranking) de LaLiga.
 */
@Schema(description = "Fila de la clasificación")
public record RankingEntryDto(
        @Schema(description = "Posición en la tabla", example = "1") int position,
        Long teamId,
        String teamName,
        int matchesPlayed,
        int wins,
        int draws,
        int losses,
        int goalsFor,
        int goalsAgainst,
        @Schema(description = "Diferencia de goles") int goalDifference,
        @Schema(description = "Puntos totales") int points
) {
}
