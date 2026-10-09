package com.ligalytics.etl.dto;

import java.time.LocalDateTime;

/**
 * Partido tal y como lo entrega una fuente externa, antes de normalizarlo.
 * {@code corners}, {@code yellowCards} y {@code redCards} son totales del
 * partido; las tarjetas por equipo se informan además por separado cuando la
 * fuente las aporta (football-data: HY, AY, HR, AR).
 */
public record RawMatch(
        String division,
        LocalDateTime matchDate,
        String homeTeam,
        String awayTeam,
        Integer homeGoals,
        Integer awayGoals,
        Integer homeShots,
        Integer awayShots,
        Integer homeShotsOnTarget,
        Integer awayShotsOnTarget,
        Double homeXg,
        Double awayXg,
        Integer corners,
        Integer yellowCards,
        Integer redCards,
        Integer homeYellowCards,
        Integer awayYellowCards,
        Integer homeRedCards,
        Integer awayRedCards,
        Integer homeFouls,
        Integer awayFouls
) {

    /** Constructor para fuentes que no aportan faltas. */
    public RawMatch(String division, LocalDateTime matchDate, String homeTeam, String awayTeam,
            Integer homeGoals, Integer awayGoals, Integer homeShots, Integer awayShots,
            Integer homeShotsOnTarget, Integer awayShotsOnTarget, Double homeXg, Double awayXg,
            Integer corners, Integer yellowCards, Integer redCards, Integer homeYellowCards,
            Integer awayYellowCards, Integer homeRedCards, Integer awayRedCards) {
        this(division, matchDate, homeTeam, awayTeam, homeGoals, awayGoals, homeShots, awayShots,
                homeShotsOnTarget, awayShotsOnTarget, homeXg, awayXg, corners, yellowCards, redCards,
                homeYellowCards, awayYellowCards, homeRedCards, awayRedCards, null, null);
    }


    /** Constructor para fuentes que no aportan tarjetas por equipo (p. ej. FBref). */
    public RawMatch(String division, LocalDateTime matchDate, String homeTeam, String awayTeam,
            Integer homeGoals, Integer awayGoals, Integer homeShots, Integer awayShots,
            Integer homeShotsOnTarget, Integer awayShotsOnTarget, Double homeXg, Double awayXg,
            Integer corners, Integer yellowCards, Integer redCards) {
        this(division, matchDate, homeTeam, awayTeam, homeGoals, awayGoals, homeShots, awayShots,
                homeShotsOnTarget, awayShotsOnTarget, homeXg, awayXg, corners, yellowCards, redCards,
                null, null, null, null, null, null);
    }
}
