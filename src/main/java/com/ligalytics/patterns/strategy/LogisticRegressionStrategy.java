package com.ligalytics.patterns.strategy;

import java.util.Objects;

import com.ligalytics.patterns.builder.MatchAnalysis;

/**
 * Estrategia de <b>regresión logística multinomial</b> para predecir el signo
 * del partido (victoria local, empate o victoria visitante).
 *
 * <p>Modela tres logits a partir del diferencial de xG, la forma reciente y la
 * posición en la tabla, y aplica una función softmax para obtener la
 * probabilidad de cada resultado. Se selecciona la clase más probable.</p>
 */
public class LogisticRegressionStrategy implements PredictionStrategy {

    private static final double XG_WEIGHT = 1.2;
    private static final double FORM_WEIGHT = 0.6;
    private static final double POSITION_WEIGHT = 0.25;
    private static final double DRAW_BIAS = 0.35;
    private static final double DRAW_XG_PENALTY = 0.8;
    private static final double DRAW_FORM_PENALTY = 0.4;

    @Override
    public String name() {
        return "logistic-regression";
    }

    @Override
    public String target() {
        return "resultado";
    }

    @Override
    public Prediction predict(MatchAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis must not be null");

        double xgDiff = strengthDifference(analysis);
        double formDiff = PredictorSupport.formPoints(analysis.getHomeRecentForm())
                - PredictorSupport.formPoints(analysis.getAwayRecentForm());
        double positionDiff = PredictorSupport.positionAdvantage(
                analysis.getHomeLeaguePosition(), analysis.getAwayLeaguePosition());

        double homeLogit = XG_WEIGHT * xgDiff + FORM_WEIGHT * formDiff + POSITION_WEIGHT * positionDiff;
        double awayLogit = -homeLogit;
        double drawLogit = DRAW_BIAS - DRAW_XG_PENALTY * Math.abs(xgDiff) - DRAW_FORM_PENALTY * Math.abs(formDiff);

        double[] probabilities = softmax(homeLogit, drawLogit, awayLogit);
        double homeProbability = probabilities[0];
        double drawProbability = probabilities[1];
        double awayProbability = probabilities[2];

        String outcome;
        double confidence;
        if (homeProbability >= drawProbability && homeProbability >= awayProbability) {
            outcome = "HOME_WIN";
            confidence = homeProbability;
        } else if (awayProbability >= homeProbability && awayProbability >= drawProbability) {
            outcome = "AWAY_WIN";
            confidence = awayProbability;
        } else {
            outcome = "DRAW";
            confidence = drawProbability;
        }

        String rationale = "p(H)=" + round(homeProbability) + ", p(D)=" + round(drawProbability)
                + ", p(A)=" + round(awayProbability);
        return new Prediction(target(), outcome, homeProbability, awayProbability, confidence, rationale);
    }

    /**
     * Diferencia de fuerza ofensiva local - visitante: usa el xG cuando existe
     * y, si no, el balance de goles (a favor - en contra) de los ultimos partidos.
     */
    private static double strengthDifference(MatchAnalysis analysis) {
        if (analysis.hasExpectedGoals()) {
            return PredictorSupport.value(analysis.getHomeXg()) - PredictorSupport.value(analysis.getAwayXg());
        }
        if (analysis.hasGoalAverages()) {
            return (analysis.getHomeAverageGoalsFor() - analysis.getHomeAverageGoalsAgainst())
                    - (analysis.getAwayAverageGoalsFor() - analysis.getAwayAverageGoalsAgainst());
        }
        return 0.0;
    }

    private static double[] softmax(double... logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (double logit : logits) {
            max = Math.max(max, logit);
        }
        double sum = 0.0;
        double[] exponentials = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exponentials[i] = Math.exp(logits[i] - max);
            sum += exponentials[i];
        }
        for (int i = 0; i < exponentials.length; i++) {
            exponentials[i] /= sum;
        }
        return exponentials;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
