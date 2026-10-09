package com.ligalytics.patterns.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sujeto del patrón <b>Observer</b>. Mantiene la lista de observadores
 * registrados y les notifica los eventos de finalización del ETL.
 *
 * <p>Spring inyecta automáticamente todos los beans {@link ETLObserver}, pero
 * también se pueden registrar/desregistrar observadores dinámicamente mediante
 * {@link #register(ETLObserver)} y {@link #unregister(ETLObserver)}.</p>
 */
@Component
public class ETLSubject {

    private static final Logger log = LoggerFactory.getLogger(ETLSubject.class);

    private final List<ETLObserver> observers = new CopyOnWriteArrayList<>();

    public ETLSubject(List<ETLObserver> observers) {
        if (observers != null) {
            observers.forEach(this::register);
        }
    }

    public void register(ETLObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public boolean unregister(ETLObserver observer) {
        return observers.remove(observer);
    }

    public List<ETLObserver> observers() {
        return List.copyOf(observers);
    }

    /**
     * Notifica a todos los observadores registrados. Un fallo en un observador
     * se aísla para no interrumpir al resto ni la propia ingesta.
     */
    public void notifyObservers(EtlEvent event) {
        if (event == null) {
            return;
        }
        log.info("ETL completado [{}]: notificando a {} observadores", event.source(), observers.size());
        for (ETLObserver observer : observers) {
            try {
                observer.onEtlCompleted(event);
            } catch (RuntimeException ex) {
                log.error("El observador '{}' falló al procesar el evento [{}]", observer.name(), event.source(), ex);
            }
        }
    }
}
