package com.ligalytics.betting;

/** Mercados disponibles en la demostración. */
public enum Market {
    /** Ganador del partido (1X2): HOME, DRAW, AWAY. */
    WINNER,
    /** Más/menos goles totales: OVER, UNDER con una línea. */
    GOALS,
    /** Ambos equipos marcan: YES, NO. */
    BTTS,
    /** Más/menos córneres totales: OVER, UNDER con una línea. */
    CORNERS,
    /** Más/menos tarjetas totales (amarillas + rojas): OVER, UNDER con una línea. */
    CARDS;

    public String label() {
        return switch (this) {
            case WINNER -> "Ganador";
            case GOALS -> "Goles";
            case BTTS -> "Ambos marcan";
            case CORNERS -> "Córneres";
            case CARDS -> "Tarjetas";
        };
    }
}
