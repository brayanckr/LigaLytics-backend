package com.ligalytics.patterns.builder;

/** Claves de las variables avanzadas opcionales de {@link MatchAnalysis}. */
public final class AdvancedStats {

    // Partido ya jugado (para persistirlo)
    public static final String HOME_SHOTS = "homeShots";
    public static final String AWAY_SHOTS = "awayShots";
    public static final String HOME_SHOTS_ON_TARGET = "homeShotsOnTarget";
    public static final String AWAY_SHOTS_ON_TARGET = "awayShotsOnTarget";
    public static final String HOME_FOULS = "homeFouls";
    public static final String AWAY_FOULS = "awayFouls";

    // Estado de los equipos antes del partido (calculado por TeamFormTracker)
    public static final String HOME_ELO = "homeElo";
    public static final String AWAY_ELO = "awayElo";
    public static final String HOME_LONG_GF = "homeLongGoalsFor";
    public static final String HOME_LONG_GA = "homeLongGoalsAgainst";
    public static final String AWAY_LONG_GF = "awayLongGoalsFor";
    public static final String AWAY_LONG_GA = "awayLongGoalsAgainst";
    public static final String HOME_VENUE_GF = "homeAtHomeGoalsFor";
    public static final String HOME_VENUE_GA = "homeAtHomeGoalsAgainst";
    public static final String AWAY_VENUE_GF = "awayAwayGoalsFor";
    public static final String AWAY_VENUE_GA = "awayAwayGoalsAgainst";
    public static final String HOME_SOT_FOR = "homeShotsOnTargetFor";
    public static final String HOME_SOT_AGAINST = "homeShotsOnTargetAgainst";
    public static final String AWAY_SOT_FOR = "awayShotsOnTargetFor";
    public static final String AWAY_SOT_AGAINST = "awayShotsOnTargetAgainst";
    public static final String HOME_REST_DAYS = "homeRestDays";
    public static final String AWAY_REST_DAYS = "awayRestDays";
    /** Enfrentamientos directos previos (hasta 5) y su media de goles totales; solo si hay al menos uno. */
    public static final String H2H_MATCHES = "h2hMatches";
    public static final String H2H_AVG_GOALS = "h2hAvgGoals";
    public static final String HOME_XG_FOR = "homeXgFor";
    public static final String HOME_XG_AGAINST = "homeXgAgainst";
    public static final String AWAY_XG_FOR = "awayXgFor";
    public static final String AWAY_XG_AGAINST = "awayXgAgainst";
    /** 1.0 si ambos equipos tienen xG real reciente (al menos 3 partidos), 0.0 si no. */
    public static final String XG_AVAILABLE = "xgAvailable";

    private AdvancedStats() {
    }
}
