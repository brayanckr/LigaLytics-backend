package com.ligalytics.patterns.strategy;

/**
 * Resultado común devuelto por cualquier {@link Predictor}.
 *
 * @param target     tipo de predicción (resultado, goles, córneres, tarjetas)
 * @param outcome    etiqueta legible de la predicción (p. ej. {@code HOME_WIN})
 * @param homeValue  valor estimado para el equipo local
 * @param awayValue  valor estimado para el equipo visitante
 * @param confidence confianza normalizada entre 0 y 1
 * @param rationale  explicación breve del cálculo
 */
public record Prediction(
        String target,
        String outcome,
        double homeValue,
        double awayValue,
        double confidence,
        String rationale
) {

    public double total() {
        return homeValue + awayValue;
    }
}
