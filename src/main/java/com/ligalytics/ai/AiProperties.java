package com.ligalytics.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuración del módulo de IA (Weka): ruta de los modelos persistidos y
 * parámetros de entrenamiento.
 */
@Component
@ConfigurationProperties(prefix = "ligalytics.ai")
public class AiProperties {

    /** Directorio donde se guardan los modelos serializados (.model). */
    private String modelPath = "models/";

    /** Número mínimo de partidos para entrenar un clasificador. */
    private int minTrainingMatches = 10;

    /** Número de particiones para la validación cruzada. */
    private int crossValidationFolds = 10;

    /** Semilla para reproducibilidad de los algoritmos aleatorios. */
    private long randomSeed = 42L;

    /**
     * Año de inicio de la temporada usada como conjunto de prueba (2023 = 2023-24).
     * Las temporadas anteriores entrenan el modelo. Con 0 o menos se usa la
     * última temporada disponible.
     */
    private int testSeasonStartYear = 0;

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public int getMinTrainingMatches() {
        return minTrainingMatches;
    }

    public void setMinTrainingMatches(int minTrainingMatches) {
        this.minTrainingMatches = minTrainingMatches;
    }

    public int getCrossValidationFolds() {
        return crossValidationFolds;
    }

    public void setCrossValidationFolds(int crossValidationFolds) {
        this.crossValidationFolds = crossValidationFolds;
    }

    public int getTestSeasonStartYear() {
        return testSeasonStartYear;
    }

    public void setTestSeasonStartYear(int testSeasonStartYear) {
        this.testSeasonStartYear = testSeasonStartYear;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
    }
}
