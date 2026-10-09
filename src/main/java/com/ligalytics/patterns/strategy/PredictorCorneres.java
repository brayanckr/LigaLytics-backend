package com.ligalytics.patterns.strategy;

import com.ligalytics.patterns.builder.MatchAnalysis;

/**
 * Predictor del número de córneres. Delega el cálculo en una
 * {@link PredictionStrategy}; por defecto usa el árbol de decisión
 * {@link DecisionTreeStrategy#forCorners()}.
 */
public class PredictorCorneres implements Predictor {

    private final PredictionStrategy strategy;

    public PredictorCorneres() {
        this(DecisionTreeStrategy.forCorners());
    }

    public PredictorCorneres(PredictionStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public String target() {
        return "corneres";
    }

    @Override
    public Prediction predict(MatchAnalysis analysis) {
        return strategy.predict(analysis);
    }
}
