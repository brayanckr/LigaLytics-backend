package com.ligalytics.service.dto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Estadísticas agregadas de un equipo en una temporada junto a su historial
 * reciente de partidos.
 */
@Schema(description = "Estadísticas avanzadas e historial de un equipo")
public record TeamStatsDto(
        Long teamId,
        String teamName,
        String stadium,
        BigDecimal marketValue,
        Integer matchesPlayed,
        Integer wins,
        Integer draws,
        Integer losses,
        Integer goalsFor,
        Integer goalsAgainst,
        Integer points,
        Double averageGoalsFor,
        Double averageGoalsAgainst,
        Double averageCorners,
        Double averageYellowCards,
        List<MatchSummaryDto> recentMatches,
        @Schema(description = "Temporada de las estadísticas", example = "2023/2024") String season,
        @Schema(description = "Forma reciente (últimos 5 partidos, p. ej. WWDLW)") String recentForm
) {
}
