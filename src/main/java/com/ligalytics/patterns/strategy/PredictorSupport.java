package com.ligalytics.patterns.strategy;

/**
 * Utilidades compartidas por las implementaciones de {@link Predictor}.
 */
final class PredictorSupport {

    private PredictorSupport() {
    }

    static double value(Double number) {
        return number == null ? 0.0 : number;
    }

    static double value(Integer number) {
        return number == null ? 0.0 : number.doubleValue();
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Convierte una racha tipo {@code "WWDLW"} en puntos normalizados [0, 1],
     * donde victoria = 3, empate = 1 y derrota = 0. Devuelve 0.5 (neutral) si
     * la racha no está disponible.
     */
    static double formPoints(String form) {
        if (form == null || form.isBlank()) {
            return 0.5;
        }
        int points = 0;
        int counted = 0;
        for (char c : form.toUpperCase().toCharArray()) {
            switch (c) {
                case 'W' -> {
                    points += 3;
                    counted++;
                }
                case 'D' -> {
                    points += 1;
                    counted++;
                }
                case 'L' -> counted++;
                default -> {
                }
            }
        }
        if (counted == 0) {
            return 0.5;
        }
        return (double) points / (counted * 3.0);
    }

    /**
     * Ventaja por posición en la tabla: positiva si el local está mejor
     * clasificado que el visitante. Normalizada sobre 20 puestos.
     */
    static double positionAdvantage(Integer homePosition, Integer awayPosition) {
        if (homePosition == null || awayPosition == null) {
            return 0.0;
        }
        return (awayPosition - homePosition) / 20.0;
    }
}
