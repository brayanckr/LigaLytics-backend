package com.ligalytics.patterns.factory;

import java.util.Objects;

import com.ligalytics.patterns.strategy.DecisionTreeStrategy;
import com.ligalytics.patterns.strategy.LogisticRegressionStrategy;
import com.ligalytics.patterns.strategy.PoissonStrategy;
import com.ligalytics.patterns.strategy.PredictionStrategy;
import com.ligalytics.patterns.strategy.Predictor;
import com.ligalytics.patterns.strategy.PredictorCorneres;
import com.ligalytics.patterns.strategy.PredictorGoles;
import com.ligalytics.patterns.strategy.PredictorResultado;
import com.ligalytics.patterns.strategy.PredictorTarjetas;

/**
 * Patrón <b>Factory Method</b>: centraliza la creación de predictores y de las
 * estrategias de predicción (patrón <b>Strategy</b>) que estos utilizan.
 *
 * <pre>{@code
 * Predictor predictor = PredictorFactory.createPredictor("goles");
 * Prediction prediction = predictor.predict(matchAnalysis);
 *
 * PredictionStrategy strategy = PredictorFactory.createStrategy(PredictorType.GOLES);
 * }</pre>
 */
public final class PredictorFactory {

    private PredictorFactory() {
    }

    /**
     * Crea el predictor asociado al tipo indicado, inyectándole la estrategia
     * concreta correspondiente.
     *
     * @param type tipo de predictor; no puede ser {@code null}
     * @return una nueva instancia del predictor correspondiente
     */
    public static Predictor createPredictor(PredictorType type) {
        Objects.requireNonNull(type, "type must not be null");
        switch (type) {
            case RESULTADO:
                return new PredictorResultado(new LogisticRegressionStrategy());
            case GOLES:
                return new PredictorGoles(new PoissonStrategy());
            case CORNERES:
                return new PredictorCorneres(DecisionTreeStrategy.forCorners());
            case TARJETAS:
                return new PredictorTarjetas(DecisionTreeStrategy.forCards());
            default:
                throw new IllegalArgumentException("Tipo de predictor no soportado: " + type);
        }
    }

    /**
     * Crea el predictor a partir de su código de texto (por ejemplo
     * {@code "resultado"}, {@code "goles"}, {@code "corneres"} o
     * {@code "tarjetas"}).
     */
    public static Predictor createPredictor(String value) {
        return createPredictor(PredictorType.from(value));
    }

    /**
     * Crea la estrategia de predicción concreta asociada al tipo indicado.
     */
    public static PredictionStrategy createStrategy(PredictorType type) {
        Objects.requireNonNull(type, "type must not be null");
        switch (type) {
            case RESULTADO:
                return new LogisticRegressionStrategy();
            case GOLES:
                return new PoissonStrategy();
            case CORNERES:
                return DecisionTreeStrategy.forCorners();
            case TARJETAS:
                return DecisionTreeStrategy.forCards();
            default:
                throw new IllegalArgumentException("Tipo de predictor no soportado: " + type);
        }
    }

    public static PredictionStrategy createStrategy(String value) {
        return createStrategy(PredictorType.from(value));
    }
}
