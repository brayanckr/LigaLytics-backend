package com.ligalytics.ai;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

import com.ligalytics.model.Match;

/**
 * Objetivos de predicción soportados por el motor de IA.
 *
 * <ul>
 *   <li>{@link #RESULTADO}: clasificación (victoria local / empate / victoria visitante).</li>
 *   <li>{@link #GOLES}, {@link #CORNERES}, {@link #TARJETAS}: valores numéricos
 *       (total del partido). Además se publica una etiqueta OVER/UNDER respecto
 *       a una línea de referencia.</li>
 * </ul>
 */
public enum PredictionTarget {

    RESULTADO("resultado", List.of("HOME_WIN", "DRAW", "AWAY_WIN"), 0.0, ""),
    GOLES("goles", List.of(), 2.5, "2_5"),
    CORNERES("corneres", List.of(), 9.5, "9_5"),
    TARJETAS("tarjetas", List.of(), 4.5, "4_5");

    private final String code;
    private final List<String> classValues;
    private final double line;
    private final String lineLabel;

    PredictionTarget(String code, List<String> classValues, double line, String lineLabel) {
        this.code = code;
        this.classValues = classValues;
        this.line = line;
        this.lineLabel = lineLabel;
    }

    public String code() {
        return code;
    }

    /** Clases nominales (vacío si el objetivo es numérico). */
    public List<String> classValues() {
        return classValues;
    }

    public boolean isNumeric() {
        return classValues.isEmpty();
    }

    /** Línea OVER/UNDER de referencia (solo objetivos numéricos). */
    public double line() {
        return line;
    }

    public static PredictionTarget fromCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El tipo de predictor es obligatorio");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (PredictionTarget target : values()) {
            if (target.code.equals(normalized) || target.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return target;
            }
        }
        throw new IllegalArgumentException("Objetivo de predicción no soportado: " + value);
    }

    /** Etiqueta OVER/UNDER para un total estimado o real. */
    public String outcomeFor(double total) {
        return (total >= line ? "OVER_" : "UNDER_") + lineLabel;
    }

    /**
     * Etiqueta de clase de un partido histórico para objetivos nominales, o
     * {@code null} si el objetivo es numérico o faltan datos.
     */
    public String labelFor(Match match) {
        if (this != RESULTADO) {
            return null;
        }
        Integer home = match.getFullTimeHomeGoals();
        Integer away = match.getFullTimeAwayGoals();
        if (home == null || away == null) {
            return null;
        }
        return home > away ? "HOME_WIN" : home < away ? "AWAY_WIN" : "DRAW";
    }

    /**
     * Valor real (total del partido) para objetivos numéricos; vacío si faltan
     * datos o el objetivo es nominal.
     */
    public OptionalDouble numericValue(Match match) {
        return switch (this) {
            case RESULTADO -> OptionalDouble.empty();
            case GOLES -> match.getFullTimeHomeGoals() == null || match.getFullTimeAwayGoals() == null
                    ? OptionalDouble.empty()
                    : OptionalDouble.of(match.getFullTimeHomeGoals() + match.getFullTimeAwayGoals());
            case CORNERES -> match.getCorners() == null
                    ? OptionalDouble.empty()
                    : OptionalDouble.of(match.getCorners());
            case TARJETAS -> match.getYellowCards() == null
                    ? OptionalDouble.empty()
                    : OptionalDouble.of(match.getYellowCards()
                            + (match.getRedCards() == null ? 0 : match.getRedCards()));
        };
    }
}
