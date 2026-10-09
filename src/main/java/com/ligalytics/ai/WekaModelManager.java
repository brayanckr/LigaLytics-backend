package com.ligalytics.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import weka.classifiers.Classifier;
import weka.core.SerializationHelper;

/**
 * Gestor de persistencia de modelos Weka. Serializa y carga clasificadores en
 * disco ({@code .model}) para no reentrenar en cada petición REST, manteniendo
 * además una caché en memoria de los modelos ya leídos.
 */
@Component
public class WekaModelManager {

    private static final Logger log = LoggerFactory.getLogger(WekaModelManager.class);

    private final Path modelDirectory;
    private final ConcurrentMap<PredictionTarget, Classifier> cache = new ConcurrentHashMap<>();

    public WekaModelManager(AiProperties properties) {
        this.modelDirectory = Path.of(properties.getModelPath());
    }

    public Path modelDirectory() {
        return modelDirectory;
    }

    public Path modelFile(PredictionTarget target) {
        return modelDirectory.resolve(target.code() + "-f" + MatchFeatureExtractor.featureNames().size() + ".model");
    }

    /**
     * Serializa el clasificador entrenado y actualiza la caché en memoria.
     */
    public synchronized void save(PredictionTarget target, Classifier classifier) throws IOException {
        Files.createDirectories(modelDirectory);
        Path file = modelFile(target);
        try {
            SerializationHelper.write(file.toString(), classifier);
        } catch (Exception ex) {
            throw new IOException("No se pudo guardar el modelo " + target.code(), ex);
        }
        cache.put(target, classifier);
        log.info("Modelo Weka guardado en {}", file);
    }

    /**
     * Carga un modelo desde la caché o desde disco, si existe.
     */
    public Optional<Classifier> load(PredictionTarget target) {
        Classifier cached = cache.get(target);
        if (cached != null) {
            return Optional.of(cached);
        }
        Classifier loaded = readFromDisk(target);
        if (loaded != null) {
            cache.put(target, loaded);
        }
        return Optional.ofNullable(loaded);
    }

    public boolean exists(PredictionTarget target) {
        return Files.isRegularFile(modelFile(target));
    }

    public void clearCache() {
        cache.clear();
    }

    /**
     * Elimina todos los modelos persistidos y la caché (útil en tests).
     */
    public void deleteAll() {
        cache.clear();
        for (PredictionTarget target : PredictionTarget.values()) {
            try {
                Files.deleteIfExists(modelFile(target));
            } catch (IOException ex) {
                log.warn("No se pudo eliminar el modelo {}: {}", modelFile(target), ex.getMessage());
            }
        }
    }

    private Classifier readFromDisk(PredictionTarget target) {
        Path file = modelFile(target);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return (Classifier) SerializationHelper.read(file.toString());
        } catch (Exception ex) {
            log.warn("No se pudo cargar el modelo {}: {}", file, ex.getMessage());
            return null;
        }
    }
}
