package com.ligalytics.patterns.strategy;

import com.ligalytics.patterns.builder.MatchAnalysis;

/**
 * Estrategia de predicción intercambiable. Cada implementación calcula un tipo
 * concreto de predicción (resultado, goles, córneres o tarjetas) a partir de un
 * {@link MatchAnalysis}.
 *
 * <p>El patrón <b>Factory Method</b> ({@code PredictorFactory}) es el encargado
 * de instanciar dinámicamente la implementación adecuada.</p>
 */
public interface Predictor {

    /**
     * Identificador del tipo de predicción (por ejemplo {@code "resultado"}).
     */
    String target();

    /**
     * Calcula la predicción para el partido analizado.
     *
     * @param analysis partido enriquecido; no puede ser {@code null}
     * @return la predicción resultante
     */
    Prediction predict(MatchAnalysis analysis);
}
