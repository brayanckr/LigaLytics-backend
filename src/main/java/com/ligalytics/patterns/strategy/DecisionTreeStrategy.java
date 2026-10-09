package com.ligalytics.patterns.strategy;

import java.util.Objects;

import com.ligalytics.patterns.builder.MatchAnalysis;

/**
 * Estrategia de <b>árbol de decisión</b> heurístico para córneres y tarjetas.
 * Es la estrategia de respaldo cuando todavía no hay un modelo entrenado
 * ({@link WekaStrategy} con REPTree): el nodo raíz compara el total esperado
 * con la línea de mercado y devuelve la hoja OVER/UNDER.
 *
 * <ul>
 *   <li>Córneres: promedio de córneres totales en los partidos de cada equipo
 *       → total esperado = media de ambos.</li>
 *   <li>Tarjetas: tarjetas propias por partido de cada equipo → total esperado
 *       = suma de ambos.</li>
 * </ul>
 */
public class DecisionTreeStrategy implements PredictionStrategy {

    public enum Feature {
        CORNERS,
        YELLOW_CARDS
    }

    private final String target;
    private final Feature feature;
    private final double line;
    private final String overOutcome;
    private final String underOutcome;

    private DecisionTreeStrategy(String target,
            Feature feature,
            double line,
            String overOutcome,
            String underOutcome) {
        this.target = target;
        this.feature = feature;
        this.line = line;
        this.overOutcome = overOutcome;
        this.underOutcome = underOutcome;
    }

    public static DecisionTreeStrategy forCorners() {
        return new DecisionTreeStrategy("corneres", Feature.CORNERS, 9.5, "OVER_9_5", "UNDER_9_5");
    }

    public static DecisionTreeStrategy forCards() {
        return new DecisionTreeStrategy("tarjetas", Feature.YELLOW_CARDS, 4.5, "OVER_4_5", "UNDER_4_5");
    }

    @Override
    public String name() {
        return "decision-tree";
    }

    @Override
    public String target() {
        return target;
    }

    @Override
    public Prediction predict(MatchAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis must not be null");

        double home;
        double away;
        if (feature == Feature.CORNERS) {
            double mean = (PredictorSupport.value(analysis.getHomeAverageCorners())
                    + PredictorSupport.value(analysis.getAwayAverageCorners())) / 2.0;
            home = mean / 2.0;
            away = mean / 2.0;
        } else {
            home = PredictorSupport.value(analysis.getHomeAverageYellowCards())
                    + PredictorSupport.value(analysis.getHomeAverageRedCards());
            away = PredictorSupport.value(analysis.getAwayAverageYellowCards())
                    + PredictorSupport.value(analysis.getAwayAverageRedCards());
        }
        double total = home + away;

        if (total == 0.0) {
            Integer observed = feature == Feature.CORNERS ? analysis.getCorners() : analysis.getYellowCards();
            if (observed != null) {
                total = observed;
                home = total / 2.0;
                away = total / 2.0;
            }
        }

        // Nodo raíz: ¿el total esperado supera la línea?
        boolean over = total >= line;
        String outcome = over ? overOutcome : underOutcome;
        double distance = Math.abs(total - line);
        double confidence = PredictorSupport.clamp(0.5 + distance / (2.0 * line), 0.0, 1.0);

        String rationale = "total esperado=" + round(total) + ", umbral=" + line;
        return new Prediction(target, outcome, home, away, confidence, rationale);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
