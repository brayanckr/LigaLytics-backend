package com.ligalytics.patterns.strategy;

import com.ligalytics.patterns.builder.MatchAnalysis;

/**
 * Predictor del número de goles. Delega el cálculo en una
 * {@link PredictionStrategy}; por defecto usa {@link PoissonStrategy}.
 */
public class PredictorGoles implements Predictor {

    private final PredictionStrategy strategy;

    public PredictorGoles() {
        this(new PoissonStrategy());
    }

    public PredictorGoles(PredictionStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public String target() {
        return "goles";
    }

    @Override
    public Prediction predict(MatchAnalysis analysis) {
        return strategy.predict(analysis);
    }
}
