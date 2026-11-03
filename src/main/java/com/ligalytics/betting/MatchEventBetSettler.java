package com.ligalytics.betting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ligalytics.webhook.MatchEventPayload;
import com.ligalytics.webhook.MatchEventReceived;

/**
 * Liquida las apuestas de demostración cuando el webhook del proveedor anuncia el final de un partido.
 *
 * <p>Solo reacciona a eventos {@code FULL_TIME}, que deben traer {@code eventId} (el mismo identificador de
 * partido con el que se hicieron las apuestas) y el marcador final; {@code corners} y {@code cards} son
 * opcionales. El resto de eventos (goles, tarjetas, córners...) se ignoran aquí. Si un {@code FULL_TIME} llega
 * sin marcador válido se lanza {@link IllegalArgumentException} (el webhook responde 400) para que el proveedor
 * se entere del error en lugar de perder el resultado en silencio. Como el evento ya está autenticado y la
 * liquidación es idempotente, que el proveedor reintente es seguro.</p>
 */
@Component
public class MatchEventBetSettler {

    static final String FULL_TIME = "FULL_TIME";
    /** Marcadores y totales fuera de este rango no son de un partido real. */
    private static final int MAX_GOALS = 50;
    private static final int MAX_TOTAL = 200;
    private static final Logger log = LoggerFactory.getLogger(MatchEventBetSettler.class);

    private final BettingService bettingService;

    public MatchEventBetSettler(BettingService bettingService) {
        this.bettingService = bettingService;
    }

    @EventListener
    public void onMatchEvent(MatchEventReceived received) {
        MatchEventPayload event = received.payload();
        if (!FULL_TIME.equalsIgnoreCase(event.type())) {
            return;
        }
        long eventId;
        try {
            eventId = Long.parseLong(event.eventId().trim());
        } catch (NumberFormatException ex) {
            log.warn("FULL_TIME ignorado: el eventId '{}' no es numérico, no corresponde a ninguna apuesta", event.eventId());
            return;
        }
        requireInRange(event.homeScore(), MAX_GOALS, "homeScore", true);
        requireInRange(event.awayScore(), MAX_GOALS, "awayScore", true);
        requireInRange(event.corners(), MAX_TOTAL, "corners", false);
        requireInRange(event.cards(), MAX_TOTAL, "cards", false);

        int settled = bettingService.settleEvent(eventId, event.homeScore(), event.awayScore(), event.corners(), event.cards());
        log.info("Apuestas liquidadas por webhook (partido {}, {}-{}): {}", eventId, event.homeScore(), event.awayScore(), settled);
    }

    private static void requireInRange(Integer value, int max, String field, boolean required) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("El evento FULL_TIME debe incluir " + field);
            }
            return;
        }
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("El valor de " + field + " no es válido");
        }
    }
}
