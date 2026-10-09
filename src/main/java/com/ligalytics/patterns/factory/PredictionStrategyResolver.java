package com.ligalytics.patterns.factory;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ligalytics.ai.MatchDatasetConverter;
import com.ligalytics.ai.PredictionTarget;
import com.ligalytics.ai.WekaModelManager;
import com.ligalytics.patterns.strategy.PoissonStrategy;
import com.ligalytics.patterns.strategy.PredictionStrategy;
import com.ligalytics.patterns.strategy.Predictor;
import com.ligalytics.patterns.strategy.PredictorCorneres;
import com.ligalytics.patterns.strategy.PredictorGoles;
import com.ligalytics.patterns.strategy.PredictorResultado;
import com.ligalytics.patterns.strategy.PredictorTarjetas;
import com.ligalytics.patterns.strategy.WekaStrategy;

import weka.classifiers.Classifier;

/**
 * Puente entre el motor de IA y el patrón <b>Strategy</b>. Si existe un modelo
 * Weka entrenado para el objetivo solicitado (resultado, córneres, tarjetas),
 * devuelve una {@link WekaStrategy} respaldada por él; en caso contrario
 * delega en {@link PredictorFactory} para obtener la estrategia heurística
 * equivalente. Los goles siempre usan {@link PoissonStrategy}.
 */
@Component
public class PredictionStrategyResolver {

    private final WekaModelManager modelManager;
    private final MatchDatasetConverter datasetConverter;

    public PredictionStrategyResolver(WekaModelManager modelManager, MatchDatasetConverter datasetConverter) {
        this.modelManager = modelManager;
        this.datasetConverter = datasetConverter;
    }

    /**
     * Devuelve la estrategia Weka si hay modelo entrenado; si no, la heurística.
     */
    public PredictionStrategy resolveStrategy(PredictorType type) {
        PredictionTarget target = PredictionTarget.fromCode(type.code());
        if (type == PredictorType.GOLES) {
            return PredictorFactory.createStrategy(type);
        }
        Optional<Classifier> model = modelManager.load(target);
        if (model.isPresent()) {
            return new WekaStrategy(target, model.get(), datasetConverter.header(target));
        }
        return PredictorFactory.createStrategy(type);
    }

    /**
     * Devuelve el predictor del tipo solicitado usando la estrategia resuelta.
     */
    public Predictor resolvePredictor(PredictorType type) {
        return predictorFor(type, resolveStrategy(type));
    }

    /** Envuelve una estrategia ya resuelta en el predictor del tipo indicado. */
    public Predictor predictorFor(PredictorType type, PredictionStrategy strategy) {
        return switch (type) {
            case RESULTADO -> new PredictorResultado(strategy);
            case GOLES -> new PredictorGoles(strategy);
            case CORNERES -> new PredictorCorneres(strategy);
            case TARJETAS -> new PredictorTarjetas(strategy);
        };
    }

    public boolean hasTrainedModel(PredictorType type) {
        return type != PredictorType.GOLES && modelManager.exists(PredictionTarget.fromCode(type.code()));
    }
}
