package com.ligalytics.external;

/**
 * Probabilidades de ganador de un modelo externo para un partido, con los goles
 * esperados por equipo cuando la fuente los aporta.
 *
 * @param home  probabilidad de victoria local (0-1)
 * @param draw  probabilidad de empate (1 - local - visitante)
 * @param away  probabilidad de victoria visitante (0-1)
 * @param source nombre de la fuente (p. ej. {@code football-charts})
 * @param expectedHomeGoals goles esperados del local según la fuente (puede ser nulo)
 * @param expectedAwayGoals goles esperados del visitante según la fuente (puede ser nulo)
 */
public record WinnerOdds(double home, double draw, double away, String source,
        Double expectedHomeGoals, Double expectedAwayGoals) {

    public static WinnerOdds of(double home, double away, String source) {
        return of(home, away, source, null, null);
    }

    public static WinnerOdds of(double home, double away, String source, Double expectedHomeGoals,
            Double expectedAwayGoals) {
        double h = Math.max(0.0, Math.min(1.0, home));
        double a = Math.max(0.0, Math.min(1.0, away));
        double d = Math.max(0.0, 1.0 - h - a);
        double sum = h + d + a;
        return new WinnerOdds(h / sum, d / sum, a / sum, source, expectedHomeGoals, expectedAwayGoals);
    }

    /** Total de goles esperado según la fuente, o {@code null} si no lo aporta. */
    public Double expectedTotalGoals() {
        return expectedHomeGoals == null || expectedAwayGoals == null ? null : expectedHomeGoals + expectedAwayGoals;
    }

    /** Resultado más probable: HOME_WIN, DRAW o AWAY_WIN. */
    public String outcome() {
        if (home >= draw && home >= away) {
            return "HOME_WIN";
        }
        return away >= draw ? "AWAY_WIN" : "DRAW";
    }
}
