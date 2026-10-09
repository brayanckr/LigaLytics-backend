package com.ligalytics.patterns.strategy;

import java.util.Locale;
import java.util.Objects;

import com.ligalytics.ai.MatchFeatureExtractor;
import com.ligalytics.ai.PredictionTarget;
import com.ligalytics.patterns.builder.MatchAnalysis;

import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;

/**
 * Estrategia de predicción respaldada por un modelo de Weka entrenado con datos
 * históricos: regresión logística para el resultado (probabilidades 1X2) y
 * árbol de regresión (REPTree) para córneres y tarjetas (total esperado).
 * Traduce un {@link MatchAnalysis} al vector de características compartido
 * ({@link MatchFeatureExtractor}) y consulta el modelo.
 *
 * <p>Se usa cuando existe un modelo entrenado; en caso contrario la fábrica cae
 * de vuelta a las estrategias heurísticas.</p>
 */
public class WekaStrategy implements PredictionStrategy {

    private final PredictionTarget target;
    private final Classifier classifier;
    private final Instances dataset;

    public WekaStrategy(PredictionTarget target, Classifier classifier, Instances dataset) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
        this.dataset = Objects.requireNonNull(dataset, "dataset must not be null");
    }

    @Override
    public String name() {
        return "weka-" + classifier.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    @Override
    public String target() {
        return target.code();
    }

    @Override
    public Prediction predict(MatchAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis must not be null");
        Instance instance = MatchFeatureExtractor.toInstance(dataset, analysis);
        try {
            return target.isNumeric() ? predictNumeric(instance, analysis) : predictNominal(instance);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar la predicción con Weka para " + target.code(), ex);
        }
    }

    private Prediction predictNominal(Instance instance) throws Exception {
        double[] distribution = classifier.distributionForInstance(instance);
        int best = Utils.maxIndex(distribution);
        String outcome = dataset.classAttribute().value(best);
        double confidence = distribution[best];

        double homeValue = probabilityOf(distribution, "HOME_WIN");
        double awayValue = probabilityOf(distribution, "AWAY_WIN");
        String rationale = "modelo Weka " + classifier.getClass().getSimpleName()
                + " (confianza=" + round(confidence) + ")";
        return new Prediction(target(), outcome, homeValue, awayValue, confidence, rationale);
    }

    private Prediction predictNumeric(Instance instance, MatchAnalysis analysis) throws Exception {
        double total = Math.max(0.0, classifier.classifyInstance(instance));
        String outcome = target.outcomeFor(total);

        double homeShare = 0.5;
        if (target == PredictionTarget.TARJETAS) {
            double home = value(analysis.getHomeAverageYellowCards()) + value(analysis.getHomeAverageRedCards());
            double away = value(analysis.getAwayAverageYellowCards()) + value(analysis.getAwayAverageRedCards());
            homeShare = home + away > 0.0 ? home / (home + away) : 0.5;
        }

        double confidence = PredictorSupport.clamp(0.5 + Math.abs(total - target.line()) / (2.0 * target.line()),
                0.0, 1.0);
        String rationale = "modelo Weka " + classifier.getClass().getSimpleName()
                + " (total estimado=" + round(total) + ", línea=" + target.line() + ")";
        return new Prediction(target(), outcome, total * homeShare, total * (1.0 - homeShare), confidence, rationale);
    }

    private double probabilityOf(double[] distribution, String label) {
        int index = dataset.classAttribute().indexOfValue(label);
        if (index < 0 || index >= distribution.length) {
            return 0.0;
        }
        return distribution[index];
    }

    private static double value(Double number) {
        return number == null ? 0.0 : number;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
