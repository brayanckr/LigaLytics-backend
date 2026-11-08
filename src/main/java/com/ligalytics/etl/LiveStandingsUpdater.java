package com.ligalytics.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ligalytics.webhook.LiveMatchBroadcaster;
import com.ligalytics.webhook.MatchEventPayload;
import com.ligalytics.webhook.MatchEventReceived;

/**
 * Mantiene la clasificacion al dia en tiempo real: cuando el directo (alimentador o webhook) anuncia un
 * {@code FULL_TIME}, guarda el resultado final en la base de datos y avisa por STOMP a {@code /topic/ranking} para
 * que los clientes vuelvan a pedir la tabla. Antes la tabla solo cambiaba con la sincronizacion periodica del
 * calendario. Corre antes que la liquidacion de apuestas y no propaga errores: un fallo aqui no debe impedir
 * liquidar apuestas ni emitir el evento.
 */
@Component
public class LiveStandingsUpdater {

    private static final Logger log = LoggerFactory.getLogger(LiveStandingsUpdater.class);
    private static final int MAX_GOALS = 50;

    private final EtlService etlService;
    private final LiveMatchBroadcaster broadcaster;

    public LiveStandingsUpdater(EtlService etlService, LiveMatchBroadcaster broadcaster) {
        this.etlService = etlService;
        this.broadcaster = broadcaster;
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onMatchEvent(MatchEventReceived received) {
        MatchEventPayload event = received.payload();
        if (!"FULL_TIME".equalsIgnoreCase(event.type()) || event.homeScore() == null || event.awayScore() == null
                || event.homeScore() < 0 || event.awayScore() < 0 || event.homeScore() > MAX_GOALS
                || event.awayScore() > MAX_GOALS) {
            return;
        }
        try {
            if (etlService.ingestFinishedMatch(event.homeTeam(), event.awayTeam(), event.homeScore(), event.awayScore())) {
                log.info("Clasificacion actualizada por directo: {} {}-{} {}", event.homeTeam(), event.homeScore(),
                        event.awayScore(), event.awayTeam());
                broadcaster.broadcastRankingChanged();
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudo guardar el resultado final del partido {}: {}", event.eventId(), ex.getMessage());
        }
    }
}
