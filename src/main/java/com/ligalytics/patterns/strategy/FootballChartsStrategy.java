package com.ligalytics.patterns.strategy;

import java.util.Objects;

import com.ligalytics.external.WinnerOdds;
import com.ligalytics.patterns.builder.MatchAnalysis;

/**
 * Estrategia de ganador respaldada por un modelo externo (Football Charts,
 * Dixon-Coles). Recibe las probabilidades ya obtenidas para el partido y las
 * traduce al contrato {@link PredictionStrategy}; el resto de la aplicación no
 * distingue si el 1X2 viene del modelo propio o del externo.
 */
public class FootballChartsStrategy implements PredictionStrategy {

    private final WinnerOdds odds;

    public FootballChartsStrategy(WinnerOdds odds) {
        this.odds = Objects.requireNonNull(odds, "odds must not be null");
    }

    @Override
    public String name() {
        return odds.source();
    }

    @Override
    public String target() {
        return "resultado";
    }

    @Override
    public Prediction predict(MatchAnalysis analysis) {
        double confidence = Math.max(odds.home(), Math.max(odds.draw(), odds.away()));
        String rationale = "modelo externo " + odds.source() + ": p(H)=" + round(odds.home())
                + ", p(D)=" + round(odds.draw()) + ", p(A)=" + round(odds.away());
        return new Prediction(target(), odds.outcome(), odds.home(), odds.away(), confidence, rationale);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
