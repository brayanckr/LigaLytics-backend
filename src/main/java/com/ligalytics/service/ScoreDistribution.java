package com.ligalytics.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Distribución de marcadores de un partido (Poisson independiente) calibrada
 * para que <b>concuerde con las probabilidades de ganador</b>.
 *
 * <p>Dado el total de goles esperado {@code T} y las probabilidades de victoria
 * local y visitante, busca el reparto {@code λlocal + λvisitante = T} tal que
 * {@code P(local gana) − P(visitante gana)} de la matriz coincida con
 * {@code pLocal − pVisitante}. De esa única matriz salen, todos coherentes
 * entre sí, los goles esperados de cada equipo, el marcador más probable
 * (también el más probable <i>dado el ganador previsto</i>), el top de
 * marcadores, P(más de 2,5 goles) y P(ambos marcan).</p>
 */
public final class ScoreDistribution {

    /** Marcador con su probabilidad. */
    public record ScoreProbability(String score, double probability) {
    }

    private static final int MAX_GOALS = 10;
    private static final double MIN_TOTAL = 0.8;
    private static final double MAX_TOTAL = 5.0;

    private final double homeLambda;
    private final double awayLambda;
    private final double[][] matrix;

    private ScoreDistribution(double homeLambda, double awayLambda) {
        this.homeLambda = homeLambda;
        this.awayLambda = awayLambda;
        this.matrix = buildMatrix(homeLambda, awayLambda);
    }

    /**
     * Calibra la distribución a las probabilidades de ganador dadas.
     *
     * @param expectedTotalGoals goles totales esperados del partido
     * @param homeWin            probabilidad de victoria local (0-1)
     * @param awayWin            probabilidad de victoria visitante (0-1)
     */
    public static ScoreDistribution calibrate(double expectedTotalGoals, double homeWin, double awayWin) {
        double total = Math.max(MIN_TOTAL, Math.min(MAX_TOTAL, expectedTotalGoals));
        double target = homeWin - awayWin;

        double low = 0.02;
        double high = 0.98;
        for (int i = 0; i < 60; i++) {
            double share = (low + high) / 2.0;
            ScoreDistribution candidate = new ScoreDistribution(share * total, (1.0 - share) * total);
            if (candidate.homeWin() - candidate.awayWin() < target) {
                low = share;
            } else {
                high = share;
            }
        }
        double share = (low + high) / 2.0;
        return new ScoreDistribution(share * total, (1.0 - share) * total);
    }

    public double homeLambda() {
        return homeLambda;
    }

    public double awayLambda() {
        return awayLambda;
    }

    public double homeWin() {
        return sum((h, a) -> h > a);
    }

    public double draw() {
        return sum((h, a) -> h == a);
    }

    public double awayWin() {
        return sum((h, a) -> h < a);
    }

    /** Probabilidad de más de 2,5 goles. */
    public double over25() {
        return sum((h, a) -> h + a >= 3);
    }

    /** Probabilidad de que marquen ambos equipos. */
    public double bothTeamsScore() {
        return sum((h, a) -> h > 0 && a > 0);
    }

    /** Los {@code n} marcadores más probables. */
    public List<ScoreProbability> topScores(int n) {
        List<ScoreProbability> scores = new ArrayList<>();
        for (int h = 0; h <= MAX_GOALS; h++) {
            for (int a = 0; a <= MAX_GOALS; a++) {
                scores.add(new ScoreProbability(h + "-" + a, matrix[h][a]));
            }
        }
        scores.sort(Comparator.comparingDouble(ScoreProbability::probability).reversed());
        return scores.subList(0, Math.min(n, scores.size()));
    }

    /**
     * Marcador más probable entre los que cumplen el resultado previsto
     * ({@code HOME_WIN}, {@code DRAW} o {@code AWAY_WIN}).
     */
    public String mostLikelyScore(String outcome) {
        int bestHome = 0;
        int bestAway = 0;
        double best = -1.0;
        for (int h = 0; h <= MAX_GOALS; h++) {
            for (int a = 0; a <= MAX_GOALS; a++) {
                boolean matches = switch (outcome) {
                    case "HOME_WIN" -> h > a;
                    case "AWAY_WIN" -> h < a;
                    default -> h == a;
                };
                if (matches && matrix[h][a] > best) {
                    best = matrix[h][a];
                    bestHome = h;
                    bestAway = a;
                }
            }
        }
        return bestHome + "-" + bestAway;
    }

    private interface ScorePredicate {
        boolean test(int home, int away);
    }

    private double sum(ScorePredicate predicate) {
        double total = 0.0;
        for (int h = 0; h <= MAX_GOALS; h++) {
            for (int a = 0; a <= MAX_GOALS; a++) {
                if (predicate.test(h, a)) {
                    total += matrix[h][a];
                }
            }
        }
        return total;
    }

    private static double[][] buildMatrix(double homeLambda, double awayLambda) {
        double[][] matrix = new double[MAX_GOALS + 1][MAX_GOALS + 1];
        double total = 0.0;
        for (int h = 0; h <= MAX_GOALS; h++) {
            for (int a = 0; a <= MAX_GOALS; a++) {
                matrix[h][a] = pmf(homeLambda, h) * pmf(awayLambda, a);
                total += matrix[h][a];
            }
        }
        for (int h = 0; h <= MAX_GOALS; h++) {
            for (int a = 0; a <= MAX_GOALS; a++) {
                matrix[h][a] /= total;
            }
        }
        return matrix;
    }

    private static double pmf(double lambda, int goals) {
        double factorial = 1.0;
        for (int i = 2; i <= goals; i++) {
            factorial *= i;
        }
        return Math.exp(-lambda) * Math.pow(lambda, goals) / factorial;
    }
}
