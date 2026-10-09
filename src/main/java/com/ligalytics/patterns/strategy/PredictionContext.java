package com.ligalytics.patterns.strategy;

import java.util.Objects;

import com.ligalytics.patterns.builder.MatchAnalysis;

/**
 * Contexto del patrón <b>Strategy</b>. Mantiene una estrategia de predicción y
 * permite intercambiarla en tiempo de ejecución mediante {@link #setStrategy}.
 * El cliente invoca siempre {@link #predict(MatchAnalysis)} sin conocer la
 * implementación concreta.
 */
public class PredictionContext {

    private PredictionStrategy strategy;

    public PredictionContext(PredictionStrategy strategy) {
        setStrategy(strategy);
    }

    public void setStrategy(PredictionStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
    }

    public PredictionStrategy strategy() {
        return strategy;
    }

    public String strategyName() {
        return strategy.name();
    }

    public Prediction predict(MatchAnalysis analysis) {
        return strategy.predict(analysis);
    }
}
