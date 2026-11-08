package com.ligalytics.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.ligalytics.config.WebSocketConfig;

/** Retransmite por STOMP a {@code /topic/live-matches} los eventos que el webhook ya validó y procesó. */
@Service
public class LiveMatchBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LiveMatchBroadcaster.class);

    private final SimpMessagingTemplate messaging;

    public LiveMatchBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /** Avisa a los clientes de que la clasificación cambió para que la vuelvan a pedir. */
    public void broadcastRankingChanged() {
        try {
            messaging.convertAndSend(WebSocketConfig.RANKING_TOPIC,
                    java.util.Map.of("changedAt", java.time.Instant.now().toString()));
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar del cambio de clasificación: {}", ex.getMessage());
        }
    }

    /**
     * Emite el evento a los suscriptores. Un fallo al emitir no debe hacer fallar el webhook: el evento ya se
     * procesó (p. ej. se liquidaron apuestas) y el proveedor no debe reintentarlo por un problema del canal en vivo.
     */
    public void broadcast(MatchEventPayload payload) {
        try {
            messaging.convertAndSend(WebSocketConfig.LIVE_MATCHES_TOPIC, payload);
        } catch (RuntimeException ex) {
            log.warn("No se pudo emitir el evento del partido {} al canal en vivo: {}", payload.eventId(), ex.getMessage());
        }
    }
}
