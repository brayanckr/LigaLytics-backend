package com.ligalytics.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.ligalytics.service.dto.PredictionResponseDto;

/**
 * Caché en memoria de predicciones. La fachada {@code LigaLyticsFacade}
 * almacena aquí las respuestas completas de predicción y el observador
 * {@code CacheInvalidationObserver} la limpia cuando el ETL aporta datos nuevos.
 */
@Component
public class PredictionCache {

    private final ConcurrentMap<String, PredictionResponseDto> cache = new ConcurrentHashMap<>();

    public void put(String key, PredictionResponseDto prediction) {
        if (key != null && prediction != null) {
            cache.put(key, prediction);
        }
    }

    public Optional<PredictionResponseDto> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    public void invalidate(String key) {
        if (key != null) {
            cache.remove(key);
        }
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }
}
