package com.ligalytics.ai;

import java.time.Instant;
import java.util.List;

/**
 * Resultado de un proceso de entrenamiento y validación del motor de IA.
 *
 * @param trainedAt  instante del entrenamiento
 * @param matchCount número de partidos con marcador considerados
 * @param evaluation descripción del esquema de validación (p. ej. entrenamiento
 *                   2018-2023 / prueba 2023-24, o validación cruzada)
 * @param results    detalle por cada objetivo de predicción
 */
public record TrainingReport(Instant trainedAt, int matchCount, String evaluation, List<TargetResult> results) {

    public boolean anyTrained() {
        return results.stream().anyMatch(TargetResult::trained);
    }

    public long trainedModels() {
        return results.stream().filter(TargetResult::trained).count();
    }

    /**
     * Resultado de entrenar y validar un objetivo concreto.
     *
     * @param target         código del objetivo (resultado, goles, corneres, tarjetas)
     * @param algorithm      algoritmo usado
     * @param trained        si el objetivo quedó operativo
     * @param trainInstances partidos usados para entrenar
     * @param testInstances  partidos usados para validar
     * @param metricName     "accuracy" (resultado) o "MAE" (goles, córneres, tarjetas)
     * @param metric         valor de la métrica sobre el conjunto de prueba
     * @param baseline       métrica de un modelo trivial (clase mayoritaria / media) para comparar
     * @param modelPath      fichero del modelo persistido (nulo si no se persiste)
     * @param message        detalle legible
     */
    public record TargetResult(
            String target,
            String algorithm,
            boolean trained,
            int trainInstances,
            int testInstances,
            String metricName,
            Double metric,
            Double baseline,
            String modelPath,
            String message) {
    }
}
