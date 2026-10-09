package com.ligalytics.patterns.builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Interfaz del patrón <b>Builder</b> para {@link MatchAnalysis}.
 *
 * <p>Cada método permite añadir paso a paso un grupo de datos del partido y
 * devuelve el propio builder para encadenar las llamadas (API fluida). Solo
 * {@link #build()} es obligatorio al final.</p>
 */
public interface MatchAnalysisBuilder {

    MatchAnalysisBuilder teams(String homeTeam, String awayTeam);

    MatchAnalysisBuilder matchDate(LocalDateTime matchDate);

    MatchAnalysisBuilder season(String season);

    MatchAnalysisBuilder score(Integer homeGoals, Integer awayGoals);

    MatchAnalysisBuilder expectedGoals(Double homeXg, Double awayXg);

    MatchAnalysisBuilder corners(Integer corners);

    MatchAnalysisBuilder cards(Integer yellowCards, Integer redCards);

    /** Tarjetas de cada equipo en un partido ya jugado (amarillas local/visitante, rojas local/visitante). */
    MatchAnalysisBuilder teamCards(Integer homeYellow, Integer awayYellow, Integer homeRed, Integer awayRed);

    MatchAnalysisBuilder marketValues(BigDecimal homeMarketValue, BigDecimal awayMarketValue);

    MatchAnalysisBuilder recentForm(String homeRecentForm, String awayRecentForm);

    MatchAnalysisBuilder leaguePositions(Integer homeLeaguePosition, Integer awayLeaguePosition);

    MatchAnalysisBuilder averageCorners(Double homeAverageCorners, Double awayAverageCorners);

    MatchAnalysisBuilder averageYellowCards(Double homeAverageYellowCards, Double awayAverageYellowCards);

    MatchAnalysisBuilder averageRedCards(Double homeAverageRedCards, Double awayAverageRedCards);

    /** Promedios de goles a favor y en contra de cada equipo en sus últimos partidos. */
    MatchAnalysisBuilder averageGoals(Double homeFor, Double homeAgainst, Double awayFor, Double awayAgainst);

    /** Promedio de goles de los locales y de los visitantes en la liga (ventaja de campo). */
    MatchAnalysisBuilder leagueGoalAverages(Double homeGoals, Double awayGoals);

    /** Añade una variable avanzada opcional (ver {@link AdvancedStats}); los nulos se ignoran. */
    MatchAnalysisBuilder advanced(String key, Double value);

    MatchAnalysis build();
}
