package com.ligalytics.external;

/**
 * Probabilidades de ganador de un modelo externo para un partido.
 *
 * @param home  probabilidad de victoria local (0-1)
 * @param draw  probabilidad de empate (1 - local - visitante)
 * @param away  probabilidad de victoria visitante (0-1)
 * @param source nombre de la fuente (p. ej. {@code football-charts})
 */
public record WinnerOdds(double home, double draw, double away, String source) {

    public static WinnerOdds of(double home, double away, String source) {
        double h = Math.max(0.0, Math.min(1.0, home));
        double a = Math.max(0.0, Math.min(1.0, away));
        double d = Math.max(0.0, 1.0 - h - a);
        double sum = h + d + a;
        return new WinnerOdds(h / sum, d / sum, a / sum, source);
    }

    /** Resultado más probable: HOME_WIN, DRAW o AWAY_WIN. */
    public String outcome() {
        if (home >= draw && home >= away) {
            return "HOME_WIN";
        }
        return away >= draw ? "AWAY_WIN" : "DRAW";
    }
}
