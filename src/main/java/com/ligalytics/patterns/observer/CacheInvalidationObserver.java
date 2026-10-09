package com.ligalytics.patterns.observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ligalytics.service.PredictionCache;

/**
 * Observador concreto que invalida la caché de predicciones cuando llegan datos
 * nuevos, evitando servir resultados calculados con información desactualizada.
 */
@Component
public class CacheInvalidationObserver implements ETLObserver {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationObserver.class);

    private final PredictionCache predictionCache;

    public CacheInvalidationObserver(PredictionCache predictionCache) {
        this.predictionCache = predictionCache;
    }

    @Override
    public String name() {
        return "cache-invalidation";
    }

    @Override
    public void onEtlCompleted(EtlEvent event) {
        int evicted = predictionCache.size();
        if (evicted == 0) {
            return;
        }
        predictionCache.clear();
        log.info("Caché de predicciones invalidada ({} entradas) tras la ingesta [{}]", evicted, event.source());
    }
}
