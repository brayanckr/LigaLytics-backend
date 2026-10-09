package com.ligalytics.betting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.ligalytics.patterns.observer.ETLObserver;
import com.ligalytics.patterns.observer.EtlEvent;

/**
 * Observador del ETL: cuando se guardan partidos terminados se liquidan las apuestas de demostración
 * pendientes de esos partidos (patrón Observer).
 */
@Component
public class BetSettlementObserver implements ETLObserver {

    private static final Logger log = LoggerFactory.getLogger(BetSettlementObserver.class);

    private final BettingService bettingService;

    public BetSettlementObserver(@Lazy BettingService bettingService) {
        this.bettingService = bettingService;
    }

    @Override
    public String name() {
        return "bet-settlement";
    }

    @Override
    public void onEtlCompleted(EtlEvent event) {
        if (!event.hasChanges()) {
            return;
        }
        int settled = bettingService.settlePending();
        if (settled > 0) {
            log.info("Apuestas de demostracion liquidadas tras la ingesta [{}]: {}", event.source(), settled);
        }
    }
}
