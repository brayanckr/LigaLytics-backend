package com.ligalytics.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Últimos enfrentamientos directos entre dos equipos, con un resumen desde el punto de vista del primero. */
@Schema(description = "Enfrentamientos directos entre dos equipos")
public record HeadToHeadDto(
        String teamA,
        String teamB,
        Summary summary,
        List<Meeting> matches) {

    /** Partido jugado entre ambos (en cualquiera de las dos sedes). */
    public record Meeting(
            LocalDateTime date,
            String season,
            String homeTeam,
            String awayTeam,
            Integer homeGoals,
            Integer awayGoals,
            Integer corners,
            Integer cards) {
    }

    /**
     * Resumen de los partidos devueltos; las victorias se cuentan para el equipo A (el primero indicado).
     */
    public record Summary(
            int played,
            int winsA,
            int draws,
            int winsB,
            double averageTotalGoals,
            Double averageCorners,
            Double averageCards) {
    }
}
