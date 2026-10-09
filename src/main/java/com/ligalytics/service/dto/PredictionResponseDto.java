package com.ligalytics.service.dto;

import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Predicción completa de un partido, incluyendo probabilidades de resultado y
 * estimaciones de goles, córneres y tarjetas, junto con el algoritmo que
 * produjo cada valor (para que la predicción sea explicable).
 */
@Schema(description = "Predicción completa del partido")
public record PredictionResponseDto(
        Long homeTeamId,
        String homeTeam,
        Long awayTeamId,
        String awayTeam,
        @Schema(description = "Resultado más probable", example = "HOME_WIN") String predictedOutcome,
        @Schema(description = "Probabilidad de victoria local") double homeWinProbability,
        @Schema(description = "Probabilidad de empate") double drawProbability,
        @Schema(description = "Probabilidad de victoria visitante") double awayWinProbability,
        @Schema(description = "Goles esperados del equipo local") double expectedHomeGoals,
        @Schema(description = "Goles esperados del equipo visitante") double expectedAwayGoals,
        @Schema(description = "Pronóstico de goles", example = "OVER_2_5") String goalsOutcome,
        @Schema(description = "Marcador más probable", example = "2-1") String mostLikelyScore,
        @Schema(description = "Córneres esperados totales") double expectedCorners,
        @Schema(description = "Pronóstico de córneres", example = "OVER_9_5") String cornersOutcome,
        @Schema(description = "Tarjetas esperadas totales (amarillas + rojas)") double expectedCards,
        @Schema(description = "Pronóstico de tarjetas", example = "OVER_4_5") String cardsOutcome,
        @Schema(description = "Amarillas esperadas del local") double expectedHomeYellowCards,
        @Schema(description = "Amarillas esperadas del visitante") double expectedAwayYellowCards,
        @Schema(description = "Rojas esperadas del local") double expectedHomeRedCards,
        @Schema(description = "Rojas esperadas del visitante") double expectedAwayRedCards,
        @Schema(description = "Algoritmo usado por cada predictor", example = "{\"resultado\":\"weka-logistic\"}")
        Map<String, String> strategies,
        @Schema(description = "Temporada de la que proceden los datos usados", example = "2023/2024") String dataSeason,
        @Schema(description = "Origen del ganador: football-charts (modelo externo) o modelo-propio", example = "football-charts")
        String winnerSource,
        @Schema(description = "Probabilidad de victoria local según el modelo propio") double ownHomeWinProbability,
        @Schema(description = "Probabilidad de empate según el modelo propio") double ownDrawProbability,
        @Schema(description = "Probabilidad de victoria visitante según el modelo propio") double ownAwayWinProbability,
        @Schema(description = "Indica si la predicción se sirvió desde caché") boolean fromCache,
        Instant generatedAt
) {

    public PredictionResponseDto asCached() {
        return new PredictionResponseDto(homeTeamId, homeTeam, awayTeamId, awayTeam, predictedOutcome,
                homeWinProbability, drawProbability, awayWinProbability, expectedHomeGoals, expectedAwayGoals,
                goalsOutcome, mostLikelyScore, expectedCorners, cornersOutcome, expectedCards, cardsOutcome,
                expectedHomeYellowCards, expectedAwayYellowCards, expectedHomeRedCards, expectedAwayRedCards,
                strategies, dataSeason, winnerSource, ownHomeWinProbability, ownDrawProbability,
                ownAwayWinProbability, true, generatedAt);
    }
}
