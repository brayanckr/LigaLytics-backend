package com.ligalytics.patterns.strategy;

import com.ligalytics.patterns.builder.MatchAnalysis;

/**
 * Predictor del número de tarjetas. Delega el cálculo en una
 * {@link PredictionStrategy}; por defecto usa el árbol de decisión
 * {@link DecisionTreeStrategy#forCards()}.
 */
public class PredictorTarjetas implements Predictor {

    private final PredictionStrategy strategy;

    public PredictorTarjetas() {
        this(DecisionTreeStrategy.forCards());
    }

    public PredictorTarjetas(PredictionStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public String target() {
        return "tarjetas";
    }

    @Override
    public Prediction predict(MatchAnalysis analysis) {
        return strategy.predict(analysis);
    }
}
