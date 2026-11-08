package com.ligalytics.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Detalle de un partido (jugado o en vivo) con sus estadísticas comparadas. */
@Schema(description = "Detalle de un partido con estadísticas local vs visitante")
public record MatchDetailDto(
        @Schema(description = "Id en la base de datos (partidos jugados); nulo si viene del proveedor en vivo") Long matchId,
        @Schema(description = "Id del partido en el proveedor (en vivo); nulo en partidos de la base") Long eventId,
        @Schema(description = "SCHEDULED, LIVE, FINISHED u OTHER") String status,
        @Schema(description = "Minuto de juego si está en directo") Integer minute,
        @Schema(description = "Fecha y hora del partido") LocalDateTime date,
        @Schema(description = "Temporada, p. ej. 2026/2027") String season,
        Long homeTeamId,
        String homeTeam,
        Long awayTeamId,
        String awayTeam,
        Integer homeGoals,
        Integer awayGoals,
        @Schema(description = "Estadísticas disponibles; solo las que la fuente aporta") List<Stat> stats) {

    /** Una estadística comparada; los valores son numéricos (posesión en %, xG con decimales). */
    public record Stat(String key, String label, Double home, Double away) {
    }
}
