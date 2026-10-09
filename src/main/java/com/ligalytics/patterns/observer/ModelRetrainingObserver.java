package com.ligalytics.patterns.observer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.ligalytics.ai.WekaTrainingService;

import jakarta.annotation.PreDestroy;

/**
 * Observador concreto que reentrena los modelos de IA cuando llegan datos
 * nuevos del ETL.
 *
 * <p>El reentrenamiento se ejecuta en un hilo aparte y con <i>debounce</i>: si
 * el ETL notifica varias temporadas seguidas, solo se entrena una vez, unos
 * segundos después de la última notificación. Se puede desactivar con
 * {@code ligalytics.ai.retrain-on-etl=false}.</p>
 */
@Component
public class ModelRetrainingObserver implements ETLObserver {

    private static final Logger log = LoggerFactory.getLogger(ModelRetrainingObserver.class);

    private final WekaTrainingService trainingService;
    private final boolean enabled;
    private final long delaySeconds;
    private final AtomicInteger retrainRequests = new AtomicInteger();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "model-retraining");
        thread.setDaemon(true);
        return thread;
    });

    private ScheduledFuture<?> pending;

    public ModelRetrainingObserver(@Lazy WekaTrainingService trainingService,
            @Value("${ligalytics.ai.retrain-on-etl:true}") boolean enabled,
            @Value("${ligalytics.ai.retrain-delay-seconds:5}") long delaySeconds) {
        this.trainingService = trainingService;
        this.enabled = enabled;
        this.delaySeconds = delaySeconds;
    }

    @Override
    public String name() {
        return "model-retraining";
    }

    @Override
    public synchronized void onEtlCompleted(EtlEvent event) {
        if (!event.hasChanges()) {
            return;
        }
        int total = retrainRequests.incrementAndGet();
        if (!enabled) {
            log.info("Datos nuevos [{}]: reentrenamiento automático desactivado (solicitud #{})", event.source(), total);
            return;
        }
        if (pending != null && !pending.isDone()) {
            pending.cancel(false);
        }
        log.info("Datos nuevos [{}] ({} partidos): reentrenamiento de modelos de IA programado en {} s (solicitud #{})",
                event.source(), event.created() + event.updated(), delaySeconds, total);
        pending = executor.schedule(this::retrain, delaySeconds, TimeUnit.SECONDS);
    }

    public int retrainRequests() {
        return retrainRequests.get();
    }

    private void retrain() {
        try {
            trainingService.trainAll();
        } catch (RuntimeException ex) {
            log.error("Falló el reentrenamiento automático de los modelos", ex);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
