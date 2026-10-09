package com.ligalytics.betting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ligalytics.service.ScoreDistribution;
import com.ligalytics.service.dto.PredictionResponseDto;

/**
 * Probabilidad que el modelo asigna a cada selección, derivada de la predicción del partido:
 * ganador (Football Charts o modelo propio), goles y ambos marcan (matriz de marcadores calibrada al
 * ganador) y córneres/tarjetas (total esperado con una dispersión normal medida en validación).
 */
public final class MarketModel {

    /** Desviación típica del total de córneres y de tarjetas de un partido (error de validación x 1,25). */
    static final double CORNERS_SIGMA = 3.4;
    static final double CARDS_SIGMA = 2.5;
    /** Margen que añade la "casa" a las cuotas demo de tarjetas (suma de probabilidades implícitas = 1,07). */
    static final double DEMO_MARGIN = 1.07;

    static final double[] GOAL_LINES = { 0.5, 1.5, 2.5, 3.5 };
    static final double[] CARD_LINES = { 3.5, 4.5, 5.5 };

    private final PredictionResponseDto prediction;
    private final ScoreDistribution distribution;

    public MarketModel(PredictionResponseDto prediction) {
        this.prediction = prediction;
        this.distribution = ScoreDistribution.calibrate(prediction.expectedHomeGoals() + prediction.expectedAwayGoals(),
                prediction.homeWinProbability(), prediction.awayWinProbability());
    }

    /** Probabilidad del modelo para una selección, si el mercado se puede modelar. */
    public Optional<Double> probability(Market market, String selection, Double line) {
        Double p = switch (market) {
            case WINNER -> switch (selection.toUpperCase()) {
                case "HOME" -> prediction.homeWinProbability();
                case "DRAW" -> prediction.drawProbability();
                case "AWAY" -> prediction.awayWinProbability();
                default -> null;
            };
            case GOALS -> line == null ? null : overUnder(selection, distribution.totalOver(line));
            case BTTS -> switch (selection.toUpperCase()) {
                case "YES" -> distribution.bothTeamsScore();
                case "NO" -> 1.0 - distribution.bothTeamsScore();
                default -> null;
            };
            case CORNERS -> line == null ? null
                    : overUnder(selection, overNormal(prediction.expectedCorners(), CORNERS_SIGMA, line));
            case CARDS -> line == null ? null
                    : overUnder(selection, overNormal(prediction.expectedCards(), CARDS_SIGMA, line));
        };
        return p == null ? Optional.empty() : Optional.of(Math.max(0.02, Math.min(0.98, p)));
    }

    /** Cuotas "demo" de tarjetas: salen del propio modelo con un margen de casa, por lo que no tienen ventaja. */
    public List<OddsLine> demoCardOdds() {
        List<OddsLine> lines = new ArrayList<>();
        for (double line : CARD_LINES) {
            double over = Math.max(0.02, Math.min(0.98, overNormal(prediction.expectedCards(), CARDS_SIGMA, line)));
            lines.add(new OddsLine(Market.CARDS, "OVER", line, demoOdds(over), "demo"));
            lines.add(new OddsLine(Market.CARDS, "UNDER", line, demoOdds(1.0 - over), "demo"));
        }
        return lines;
    }

    static double demoOdds(double probability) {
        double odds = 1.0 / (probability * DEMO_MARGIN);
        return Math.round(Math.max(1.05, Math.min(15.0, odds)) * 100.0) / 100.0;
    }

    private static double overUnder(String selection, double over) {
        return "OVER".equalsIgnoreCase(selection) ? over : 1.0 - over;
    }

    /** P(total > línea) con total ~ Normal(media, sigma); las líneas terminan en ,5 y no hay empate con la línea. */
    static double overNormal(double mean, double sigma, double line) {
        return 1.0 - normalCdf((line - mean) / sigma);
    }

    /** Función de distribución normal estándar (aproximación de Abramowitz y Stegun, error < 1e-7). */
    static double normalCdf(double z) {
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = 0.3989423 * Math.exp(-z * z / 2.0);
        double p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        return z > 0 ? 1.0 - p : p;
    }
}
