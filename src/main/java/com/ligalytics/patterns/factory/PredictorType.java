package com.ligalytics.patterns.factory;

import java.util.Locale;

/**
 * Tipos de predictor soportados por {@link PredictorFactory}. Cada constante
 * declara el código de texto con el que la API o los servicios solicitan la
 * estrategia correspondiente.
 */
public enum PredictorType {

    RESULTADO("resultado"),
    GOLES("goles"),
    CORNERES("corneres"),
    TARJETAS("tarjetas");

    private final String code;

    PredictorType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * Resuelve un tipo a partir de su código (por ejemplo {@code "goles"}) o de
     * su nombre (por ejemplo {@code "GOLES"}), sin distinguir mayúsculas.
     *
     * @throws IllegalArgumentException si el valor no corresponde a ningún tipo
     */
    public static PredictorType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El tipo de predictor es obligatorio");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (PredictorType type : values()) {
            if (type.code.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Tipo de predictor no soportado: " + value);
    }
}
