package com.ligalytics.service.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resumen de un partido histórico de un equipo.
 */
@Schema(description = "Partido disputado por el equipo")
public record MatchSummaryDto(
        Long matchId,
        LocalDateTime date,
        String homeTeam,
        String awayTeam,
        Integer homeGoals,
        Integer awayGoals,
        Double homeXg,
        Double awayXg,
        Integer corners,
        Integer yellowCards,
        Integer redCards,
        @Schema(description = "Descripción del partido enriquecida por los decoradores (xG, valor de plantilla)")
        String description,
        @Schema(description = "Temporada del partido", example = "2026/2027")
        String season
) {
}
