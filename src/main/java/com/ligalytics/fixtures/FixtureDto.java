package com.ligalytics.fixtures;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Partido del calendario de LaLiga")
public record FixtureDto(
        String id,
        Instant utcDate,
        @Schema(description = "SCHEDULED, LIVE, FINISHED u OTHER") String status,
        Integer matchday,
        String homeTeam,
        String awayTeam,
        @Schema(description = "Id del equipo local en LigaLytics (nulo si no se reconoce)") Long homeTeamId,
        @Schema(description = "Id del equipo visitante en LigaLytics (nulo si no se reconoce)") Long awayTeamId,
        String homeCrest,
        String awayCrest,
        Integer homeGoals,
        Integer awayGoals) {
}
