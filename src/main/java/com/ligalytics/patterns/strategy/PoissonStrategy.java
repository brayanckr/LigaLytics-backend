package com.ligalytics.patterns.strategy;

import java.util.Objects;

import com.ligalytics.patterns.builder.AdvancedStats;
import com.ligalytics.patterns.builder.MatchAnalysis;

/**
 * Estrategia basada en la <b>distribución de Poisson</b> para estimar los goles
 * esperados. Cada equipo se modela como un proceso de Poisson con intensidad
 * (λ) derivada de su ataque y de la defensa rival (promedios de los últimos 10
 * partidos, ajustados por la ventaja de campo de la liga) y, si hay datos de
 * Understat, mezclada con su xG. Se calcula la probabilidad de que el total
 * supere la línea de mercado 2.5, el marcador más probable y las
 * probabilidades 1X2 implícitas.
 *
 * <p>Es un modelo estadístico puro (sin entrenamiento): cada predicción se
 * puede explicar con λ local y λ visitante.</p>
 */
public class PoissonStrategy implements PredictionStrategy {

    private static final double LINE = 2.5;
    private static final int UNDER_LIMIT = (int) Math.floor(LINE);
    private static final int MAX_GOALS = 8;

    @Override
    public String name() {
        return "poisson";
    }

    @Override
    public String target() {
        return "goles";
    }

    @Override
    public Prediction predict(MatchAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis must not be null");

        double[] lambdas = lambdas(analysis);
        double homeLambda = lambdas[0];
        double awayLambda = lambdas[1];

        double probabilityUnder = poissonProbabilitySum(homeLambda, awayLambda, UNDER_LIMIT);
        double probabilityOver = 1.0 - probabilityUnder;

        String outcome = probabilityOver >= 0.5 ? "OVER_2_5" : "UNDER_2_5";
        double confidence = PredictorSupport.clamp(Math.abs(probabilityOver - 0.5) * 2.0, 0.0, 1.0);

        String rationale = "λ local=" + round(homeLambda) + ", λ visitante=" + round(awayLambda)
                + ", P(+2.5)=" + round(probabilityOver) + ", marcador probable=" + mostLikelyScore(homeLambda, awayLambda);
        return new Prediction(target(), outcome, homeLambda, awayLambda, confidence, rationale);
    }

    /**
     * Intensidades (λ local, λ visitante). Con promedios de goles disponibles:
     * {@code λ = (ataque propio + defensa rival) / 2 × ventaja de campo}; si
     * además hay xG se promedia con él. Sin promedios se usa solo el xG.
     */
    public static double[] lambdas(MatchAnalysis analysis) {
        double home;
        double away;
        if (analysis.hasGoalAverages()) {
            double leagueHome = orDefault(analysis.getLeagueAverageHomeGoals(), 1.5);
            double leagueAway = orDefault(analysis.getLeagueAverageAwayGoals(), 1.1);
            double overall = Math.max((leagueHome + leagueAway) / 2.0, 0.1);
            // Fuerza = mitad corto plazo (10 partidos) + mitad largo plazo (memoria exponencial).
            double homeAttack = blend(analysis.getHomeAverageGoalsFor(), analysis.advanced(AdvancedStats.HOME_LONG_GF, Double.NaN));
            double homeDefence = blend(analysis.getHomeAverageGoalsAgainst(), analysis.advanced(AdvancedStats.HOME_LONG_GA, Double.NaN));
            double awayAttack = blend(analysis.getAwayAverageGoalsFor(), analysis.advanced(AdvancedStats.AWAY_LONG_GF, Double.NaN));
            double awayDefence = blend(analysis.getAwayAverageGoalsAgainst(), analysis.advanced(AdvancedStats.AWAY_LONG_GA, Double.NaN));
            home = (homeAttack + awayDefence) / 2.0 * (leagueHome / overall);
            away = (awayAttack + homeDefence) / 2.0 * (leagueAway / overall);
            if (analysis.hasAdvanced(AdvancedStats.XG_AVAILABLE)) {
                // xG real reciente (últimos 10 partidos): mejor indicador de la calidad de juego que los goles.
                if (analysis.advanced(AdvancedStats.XG_AVAILABLE, 0.0) > 0.5) {
                    double xgHome = (analysis.advanced(AdvancedStats.HOME_XG_FOR, 0.0)
                            + analysis.advanced(AdvancedStats.AWAY_XG_AGAINST, 0.0)) / 2.0 * (leagueHome / overall);
                    double xgAway = (analysis.advanced(AdvancedStats.AWAY_XG_FOR, 0.0)
                            + analysis.advanced(AdvancedStats.HOME_XG_AGAINST, 0.0)) / 2.0 * (leagueAway / overall);
                    home = 0.5 * home + 0.5 * xgHome;
                    away = 0.5 * away + 0.5 * xgAway;
                }
            } else {
                if (analysis.getHomeXg() != null && analysis.getHomeXg() > 0.0) {
                    home = 0.5 * home + 0.5 * analysis.getHomeXg();
                }
                if (analysis.getAwayXg() != null && analysis.getAwayXg() > 0.0) {
                    away = 0.5 * away + 0.5 * analysis.getAwayXg();
                }
            }
        } else {
            home = PredictorSupport.value(analysis.getHomeXg());
            away = PredictorSupport.value(analysis.getAwayXg());
        }
        // Enfrentamientos directos: con al menos 3 precedentes, el total de goles se acerca un 20 % a su media historica.
        if (analysis.advanced(AdvancedStats.H2H_MATCHES, 0.0) >= 3.0 && analysis.hasAdvanced(AdvancedStats.H2H_AVG_GOALS)
                && home + away > 0.0) {
            double total = home + away;
            double adjusted = 0.8 * total + 0.2 * analysis.advanced(AdvancedStats.H2H_AVG_GOALS, total);
            home = home * adjusted / total;
            away = away * adjusted / total;
        }
        return new double[] { PredictorSupport.clamp(home, 0.05, 6.0), PredictorSupport.clamp(away, 0.05, 6.0) };
    }

    /** Marcador más probable ("2-1") dadas las intensidades. */
    public static String mostLikelyScore(double homeLambda, double awayLambda) {
        int bestHome = 0;
        int bestAway = 0;
        double best = -1.0;
        for (int home = 0; home <= MAX_GOALS; home++) {
            for (int away = 0; away <= MAX_GOALS; away++) {
                double probability = poissonPmf(homeLambda, home) * poissonPmf(awayLambda, away);
                if (probability > best) {
                    best = probability;
                    bestHome = home;
                    bestAway = away;
                }
            }
        }
        return bestHome + "-" + bestAway;
    }

    /** Probabilidades {local, empate, visitante} implícitas en las intensidades. */
    public static double[] outcomeProbabilities(double homeLambda, double awayLambda) {
        double home = 0.0;
        double draw = 0.0;
        double away = 0.0;
        for (int h = 0; h <= MAX_GOALS; h++) {
            for (int a = 0; a <= MAX_GOALS; a++) {
                double probability = poissonPmf(homeLambda, h) * poissonPmf(awayLambda, a);
                if (h > a) {
                    home += probability;
                } else if (h == a) {
                    draw += probability;
                } else {
                    away += probability;
                }
            }
        }
        double sum = home + draw + away;
        return new double[] { home / sum, draw / sum, away / sum };
    }

    private static double poissonProbabilitySum(double homeLambda, double awayLambda, int maxGoals) {
        double total = 0.0;
        for (int home = 0; home <= maxGoals; home++) {
            for (int away = 0; away <= maxGoals - home; away++) {
                total += poissonPmf(homeLambda, home) * poissonPmf(awayLambda, away);
            }
        }
        return total;
    }

    private static double poissonPmf(double lambda, int goals) {
        return Math.exp(-lambda) * Math.pow(lambda, goals) / factorial(goals);
    }

    private static double factorial(int number) {
        double result = 1.0;
        for (int i = 2; i <= number; i++) {
            result *= i;
        }
        return result;
    }

    private static double blend(double shortTerm, double longTerm) {
        return Double.isNaN(longTerm) ? shortTerm : 0.25 * shortTerm + 0.75 * longTerm;
    }

    private static double orDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
