package com.ligalytics.patterns.strategy;

import com.ligalytics.patterns.builder.MatchAnalysis;

/**
 * Interfaz del patrón <b>Strategy</b>: encapsula un algoritmo de predicción
 * intercambiable. Los distintos modelos (regresión logística, Poisson, árbol de
 * decisión...) implementan este contrato y pueden sustituirse en tiempo de
 * ejecución sin que el cliente cambie.
 */
public interface PredictionStrategy {

    /**
     * Nombre del algoritmo (por ejemplo {@code "logistic-regression"}).
     */
    String name();

    /**
     * Tipo de predicción que produce (resultado, goles, córneres, tarjetas).
     */
    String target();

    /**
     * Ejecuta el algoritmo sobre el partido analizado.
     *
     * @param analysis partido enriquecido; no puede ser {@code null}
     * @return la predicción resultante
     */
    Prediction predict(MatchAnalysis analysis);
}
