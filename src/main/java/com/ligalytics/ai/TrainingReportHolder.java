package com.ligalytics.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Guarda el último informe de entrenamiento/validación (patrón Singleton de
 * Spring: un único bean para toda la aplicación). Se persiste como JSON junto a
 * los modelos para poder mostrar las métricas tras reiniciar el backend.
 */
@Component
public class TrainingReportHolder {

    private static final Logger log = LoggerFactory.getLogger(TrainingReportHolder.class);
    private static final String FILE_NAME = "training-report.json";

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private volatile TrainingReport latest;

    public TrainingReportHolder(AiProperties properties) {
        this.file = Path.of(properties.getModelPath()).resolve(FILE_NAME);
        this.latest = read();
    }

    public Optional<TrainingReport> latest() {
        return Optional.ofNullable(latest);
    }

    public void update(TrainingReport report) {
        this.latest = report;
        try {
            Files.createDirectories(file.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), report);
        } catch (IOException ex) {
            log.warn("No se pudo guardar el informe de entrenamiento en {}: {}", file, ex.getMessage());
        }
    }

    private TrainingReport read() {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return mapper.readValue(file.toFile(), TrainingReport.class);
        } catch (IOException ex) {
            log.warn("No se pudo leer el informe de entrenamiento {}: {}", file, ex.getMessage());
            return null;
        }
    }
}
