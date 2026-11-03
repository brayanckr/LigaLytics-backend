package com.ligalytics.betting;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Precarga las cuotas al arrancar el backend, en segundo plano, para que el primer usuario que abra «Próximos
 * partidos» no pague la carga en frío (decenas de llamadas al proveedor). Si el proveedor falla, solo se registra: no
 * impide arrancar y la primera petición real lo volverá a intentar.
 */
@Component
public class OddsWarmup {

    private static final Logger log = LoggerFactory.getLogger(OddsWarmup.class);

    private final BettingService bettingService;

    public OddsWarmup(BettingService bettingService) {
        this.bettingService = bettingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!bettingService.isConfigured()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            try {
                int matches = bettingService.board(7).size();
                log.info("Cuotas precargadas: {} partidos en {} ms", matches, System.currentTimeMillis() - start);
            } catch (RuntimeException ex) {
                log.warn("No se pudieron precargar las cuotas (se reintentará con la primera petición): {}", ex.getMessage());
            }
        });
    }
}
