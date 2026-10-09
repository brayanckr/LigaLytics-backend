package com.ligalytics.patterns.builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;

/**
 * Producto inmutable del patrón <b>Builder</b>: un partido enriquecido con las
 * más de 15 variables que consumen los predictores (xG, córneres, tarjetas,
 * valor de plantilla, forma reciente, posición en la tabla, promedios de goles,
 * etc.).
 *
 * <p>El constructor es de ámbito de paquete a propósito: los clientes deben
 * construir instancias a través de {@link #builder()} para evitar constructores
 * saturados de parámetros opcionales.</p>
 */
@Getter
public final class MatchAnalysis {

    private final String homeTeam;
    private final String awayTeam;
    private final LocalDateTime matchDate;
    private final String season;

    private final Integer homeGoals;
    private final Integer awayGoals;
    private final Double homeXg;
    private final Double awayXg;

    private final Integer corners;
    private final Integer yellowCards;
    private final Integer redCards;
    private final Integer homeYellowCards;
    private final Integer awayYellowCards;
    private final Integer homeRedCards;
    private final Integer awayRedCards;

    private final BigDecimal homeMarketValue;
    private final BigDecimal awayMarketValue;

    private final String homeRecentForm;
    private final String awayRecentForm;

    private final Integer homeLeaguePosition;
    private final Integer awayLeaguePosition;

    private final Double homeAverageCorners;
    private final Double awayAverageCorners;
    private final Double homeAverageYellowCards;
    private final Double awayAverageYellowCards;
    private final Double homeAverageRedCards;
    private final Double awayAverageRedCards;

    private final Double homeAverageGoalsFor;
    private final Double homeAverageGoalsAgainst;
    private final Double awayAverageGoalsFor;
    private final Double awayAverageGoalsAgainst;
    private final Double leagueAverageHomeGoals;
    private final Double leagueAverageAwayGoals;

    /** Variables avanzadas opcionales (Elo, tiros, descanso...) identificadas por clave; ver {@link AdvancedStats}. */
    private final java.util.Map<String, Double> advanced;

    @SuppressWarnings("java:S107")
    MatchAnalysis(String homeTeam,
            String awayTeam,
            LocalDateTime matchDate,
            String season,
            Integer homeGoals,
            Integer awayGoals,
            Double homeXg,
            Double awayXg,
            Integer corners,
            Integer yellowCards,
            Integer redCards,
            Integer homeYellowCards,
            Integer awayYellowCards,
            Integer homeRedCards,
            Integer awayRedCards,
            BigDecimal homeMarketValue,
            BigDecimal awayMarketValue,
            String homeRecentForm,
            String awayRecentForm,
            Integer homeLeaguePosition,
            Integer awayLeaguePosition,
            Double homeAverageCorners,
            Double awayAverageCorners,
            Double homeAverageYellowCards,
            Double awayAverageYellowCards,
            Double homeAverageRedCards,
            Double awayAverageRedCards,
            Double homeAverageGoalsFor,
            Double homeAverageGoalsAgainst,
            Double awayAverageGoalsFor,
            Double awayAverageGoalsAgainst,
            Double leagueAverageHomeGoals,
            Double leagueAverageAwayGoals,
            java.util.Map<String, Double> advanced) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.matchDate = matchDate;
        this.season = season;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.homeXg = homeXg;
        this.awayXg = awayXg;
        this.corners = corners;
        this.yellowCards = yellowCards;
        this.redCards = redCards;
        this.homeYellowCards = homeYellowCards;
        this.awayYellowCards = awayYellowCards;
        this.homeRedCards = homeRedCards;
        this.awayRedCards = awayRedCards;
        this.homeMarketValue = homeMarketValue;
        this.awayMarketValue = awayMarketValue;
        this.homeRecentForm = homeRecentForm;
        this.awayRecentForm = awayRecentForm;
        this.homeLeaguePosition = homeLeaguePosition;
        this.awayLeaguePosition = awayLeaguePosition;
        this.homeAverageCorners = homeAverageCorners;
        this.awayAverageCorners = awayAverageCorners;
        this.homeAverageYellowCards = homeAverageYellowCards;
        this.awayAverageYellowCards = awayAverageYellowCards;
        this.homeAverageRedCards = homeAverageRedCards;
        this.awayAverageRedCards = awayAverageRedCards;
        this.homeAverageGoalsFor = homeAverageGoalsFor;
        this.homeAverageGoalsAgainst = homeAverageGoalsAgainst;
        this.awayAverageGoalsFor = awayAverageGoalsFor;
        this.awayAverageGoalsAgainst = awayAverageGoalsAgainst;
        this.leagueAverageHomeGoals = leagueAverageHomeGoals;
        this.leagueAverageAwayGoals = leagueAverageAwayGoals;
        this.advanced = advanced == null ? java.util.Map.of() : java.util.Map.copyOf(advanced);
    }

    /**
     * Punto de entrada al patrón Builder.
     *
     * @return una nueva instancia de {@link MatchAnalysisBuilder}
     */
    public static MatchAnalysisBuilder builder() {
        return new DefaultMatchAnalysisBuilder();
    }

    /** Valor de una variable avanzada, o {@code fallback} si no se informó. */
    public double advanced(String key, double fallback) {
        Double value = advanced.get(key);
        return value == null ? fallback : value;
    }

    public boolean hasAdvanced(String key) {
        return advanced.containsKey(key);
    }

    public double totalExpectedGoals() {
        return value(homeXg) + value(awayXg);
    }

    public boolean hasExpectedGoals() {
        return homeXg != null && awayXg != null && (homeXg > 0.0 || awayXg > 0.0);
    }

    public boolean hasGoalAverages() {
        return homeAverageGoalsFor != null && homeAverageGoalsAgainst != null
                && awayAverageGoalsFor != null && awayAverageGoalsAgainst != null;
    }

    private static double value(Double number) {
        return number == null ? 0.0 : number;
    }
}
