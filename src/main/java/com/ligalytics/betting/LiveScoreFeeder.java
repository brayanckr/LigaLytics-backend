package com.ligalytics.betting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.ligalytics.etl.BzzoiroClient;
import com.ligalytics.webhook.LiveMatchBroadcaster;
import com.ligalytics.webhook.MatchEventPayload;
import com.ligalytics.webhook.MatchEventReceived;

/**
 * Alimentador del directo para cuando el proveedor no empuja eventos: consulta los partidos en juego y convierte cada
 * cambio (inicio, gol, descanso, final) en el mismo {@link MatchEventPayload} que recibe el webhook, así que sigue
 * <em>exactamente el mismo camino</em>: liquidación de apuestas ({@link MatchEventBetSettler}) y emisión por STOMP.
 *
 * <p>Solo consulta cuando hay un partido en su ventana de juego y respeta que el proveedor cachea 30 s la lista, por
 * lo que el retraso típico es de 15 a 45 s. Para menos retraso hace falta empuje real (webhook o WebSocket del
 * proveedor): este mismo alimentador se sustituiría por esa fuente sin tocar el resto.</p>
 */
@Component
public class LiveScoreFeeder {

    /** Lo último que se vio de un partido, para detectar qué cambió entre una consulta y la siguiente. */
    record Seen(String status, String period, Integer homeScore, Integer awayScore, Integer minute) {
    }

    private static final Logger log = LoggerFactory.getLogger(LiveScoreFeeder.class);

    private final BzzoiroClient client;
    private final BzzoiroOddsProvider oddsProvider;
    private final ApplicationEventPublisher publisher;
    private final LiveMatchBroadcaster broadcaster;
    private final Map<Long, Seen> seen = new ConcurrentHashMap<>();

    public LiveScoreFeeder(BzzoiroClient client, BzzoiroOddsProvider oddsProvider, ApplicationEventPublisher publisher,
            LiveMatchBroadcaster broadcaster) {
        this.client = client;
        this.oddsProvider = oddsProvider;
        this.publisher = publisher;
        this.broadcaster = broadcaster;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${ligalytics.live.poll-interval:PT15S}")
    public void poll() {
        // Se sigue consultando mientras haya un partido en juego ya observado, aunque su horario ya no esté en la ventana.
        boolean followingLive = seen.values().stream().anyMatch(state -> !"finished".equals(state.status()));
        if (!client.isConfigured() || !(followingLive || oddsProvider.hasMatchInLiveWindow())) {
            return;
        }
        List<JsonNode> live;
        try {
            live = client.liveEvents();
        } catch (RuntimeException ex) {
            log.warn("No se pudo consultar el directo: {}", ex.getMessage());
            return;
        }
        for (JsonNode node : live) {
            long id = node.path("id").asLong(0);
            if (id == 0) {
                continue;
            }
            Seen now = snapshot(node);
            for (MatchEventPayload event : diff(id, node, seen.get(id), now)) {
                publish(event);
            }
            seen.put(id, now);
        }
        // Se olvidan los partidos terminados que ya salieron de la ventana del proveedor.
        seen.keySet().removeIf(id -> live.stream().noneMatch(n -> n.path("id").asLong() == id)
                && "finished".equals(seen.get(id).status()));
    }

    private void publish(MatchEventPayload event) {
        try {
            publisher.publishEvent(new MatchEventReceived(event));
            log.info("Directo: partido {} {} ({}-{})", event.eventId(), event.type(), event.homeScore(), event.awayScore());
        } catch (RuntimeException ex) {
            // p. ej. un marcador no válido: no debe impedir el resto de eventos ni la emisión
            log.warn("Evento del directo no procesado (partido {}): {}", event.eventId(), ex.getMessage());
        }
        broadcaster.broadcast(event);
    }

    static Seen snapshot(JsonNode node) {
        return new Seen(node.path("status").asText(""), node.path("period").asText(""), intOrNull(node, "home_score"),
                intOrNull(node, "away_score"), intOrNull(node, "current_minute"));
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asInt() : null;
    }

    /**
     * Eventos que explica el paso de {@code before} a {@code now}. Pura (sin E/S) para poder probarla: inicio, gol,
     * descanso, final y, si solo avanzó el reloj o se corrigió el marcador, una actualización que solo se emite.
     */
    static List<MatchEventPayload> diff(long eventId, JsonNode node, Seen before, Seen now) {
        List<MatchEventPayload> events = new ArrayList<>();
        boolean started = "inprogress".equals(now.status()) || "penalties".equals(now.status());
        boolean finished = "finished".equals(now.status());
        boolean wasWaiting = before != null && ("notstarted".equals(before.status()) || "delayed".equals(before.status()));

        if (before == null) {
            // Primera vez que se ve: si ya está en juego (p. ej. tras un reinicio) se publica su estado actual, sin
            // inventar un gol ni un inicio que no se observó.
            if (started || finished) {
                events.add(event(eventId, node, finished ? "FULL_TIME" : "UPDATE", now));
            }
            return events;
        }
        if (wasWaiting && started) {
            events.add(event(eventId, node, "KICK_OFF", now));
        } else if (scoreIncreased(before, now)) {
            events.add(event(eventId, node, "GOAL", now));
        } else if (!"halftime".equals(before.period()) && "halftime".equals(now.period())) {
            events.add(event(eventId, node, "HALF_TIME", now));
        } else if (started && (!Objects.equals(before.minute(), now.minute())
                || !Objects.equals(before.homeScore(), now.homeScore())
                || !Objects.equals(before.awayScore(), now.awayScore()))) {
            events.add(event(eventId, node, "UPDATE", now));
        }
        // El final va aparte para que un gol en el último minuto y el pitido en la misma consulta no pierdan el final.
        if (!"finished".equals(before.status()) && finished) {
            events.add(event(eventId, node, "FULL_TIME", now));
        }
        return events;
    }

    private static boolean scoreIncreased(Seen before, Seen now) {
        return (before.homeScore() != null && now.homeScore() != null && now.homeScore() > before.homeScore())
                || (before.awayScore() != null && now.awayScore() != null && now.awayScore() > before.awayScore());
    }

    private static MatchEventPayload event(long eventId, JsonNode node, String type, Seen now) {
        return new MatchEventPayload(String.valueOf(eventId), type, node.path("home_team").asText(null),
                node.path("away_team").asText(null), now.minute(), now.homeScore(), now.awayScore(), null, null, Instant.now());
    }
}
