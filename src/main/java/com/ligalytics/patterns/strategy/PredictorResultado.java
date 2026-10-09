package com.ligalytics.patterns.strategy;

import com.ligalytics.patterns.builder.MatchAnalysis;

/**
 * Predictor del signo del partido. Delega el cálculo en una
 * {@link PredictionStrategy}; por defecto usa {@link LogisticRegressionStrategy}.
 */
public class PredictorResultado implements Predictor {

    private final PredictionStrategy strategy;

    public PredictorResultado() {
        this(new LogisticRegressionStrategy());
    }

    public PredictorResultado(PredictionStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public String target() {
        return "resultado";
    }

    @Override
    public Prediction predict(MatchAnalysis analysis) {
        return strategy.predict(analysis);
    }
}
