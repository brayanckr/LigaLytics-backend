package com.ligalytics.betting;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.ligalytics.etl.BzzoiroClient;

/**
 * Cuotas reales de los próximos partidos de LaLiga desde Bzzoiro Sports Data: consenso de varias casas de
 * apuestas para ganador, goles, ambos marcan y córneres. No existe mercado de tarjetas en esta fuente.
 * Los eventos y las cuotas se guardan 30 minutos en memoria para respetar el límite de peticiones.
 */
@Component
public class BzzoiroOddsProvider {

    /** Partido por jugar según el proveedor de cuotas. */
    public record OddsEvent(long id, Instant kickoff, String homeTeam, String awayTeam) {
    }

    private record Cached<T>(T value, Instant expiresAt) {
        boolean valid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(BzzoiroOddsProvider.class);
    private static final Duration TTL = Duration.ofMinutes(30);

    private final BzzoiroClient client;
    private final Map<Long, Cached<List<OddsLine>>> oddsCache = new ConcurrentHashMap<>();
    private volatile Cached<List<OddsEvent>> eventsCache;

    public BzzoiroOddsProvider(BzzoiroClient client) {
        this.client = client;
    }

    public boolean isConfigured() {
        return client.isConfigured();
    }

    /** Partidos por jugar en los próximos {@code days} días. */
    public synchronized List<OddsEvent> upcoming(int days) {
        Cached<List<OddsEvent>> cached = eventsCache;
        if (cached == null || !cached.valid()) {
            List<OddsEvent> events = new ArrayList<>();
            for (JsonNode node : client.upcomingEvents(LocalDate.now().minusDays(1), LocalDate.now().plusDays(14))) {
                try {
                    events.add(new OddsEvent(node.path("id").asLong(), java.time.OffsetDateTime
                            .parse(node.path("event_date").asText()).toInstant(), node.path("home_team").asText(),
                            node.path("away_team").asText()));
                } catch (RuntimeException ex) {
                    log.debug("Evento ignorado: {}", ex.getMessage());
                }
            }
            events.sort(java.util.Comparator.comparing(OddsEvent::kickoff));
            cached = new Cached<>(events, Instant.now().plus(TTL));
            eventsCache = cached;
        }
        Instant limit = Instant.now().plus(Duration.ofDays(days));
        return cached.value().stream().filter(e -> e.kickoff().isBefore(limit)).toList();
    }

    /** Cuotas reales del partido; lista vacía si el proveedor todavía no las publica. */
    public List<OddsLine> odds(long eventId) {
        Cached<List<OddsLine>> cached = oddsCache.get(eventId);
        if (cached != null && cached.valid()) {
            return cached.value();
        }
        List<OddsLine> lines = List.of();
        try {
            lines = parse(client.oddsRows(eventId));
        } catch (RuntimeException ex) {
            log.warn("No se pudieron obtener las cuotas del evento {}: {}", eventId, ex.getMessage());
            if (cached != null) {
                return cached.value();
            }
        }
        oddsCache.put(eventId, new Cached<>(lines, Instant.now().plus(TTL)));
        return lines;
    }

    /** Convierte las filas de cuotas de la API en cuotas de nuestros mercados (el resto se ignora). */
    public static List<OddsLine> parse(List<JsonNode> rows) {
        List<OddsLine> lines = new ArrayList<>();
        for (JsonNode row : rows) {
            String market = row.path("market").asText("");
            String outcome = row.path("outcome").asText("").toUpperCase();
            double odds = row.path("decimal_odds").asDouble(0.0);
            Double line = row.path("line").isNumber() ? row.path("line").asDouble() : null;
            if (odds <= 1.0) {
                continue;
            }
            OddsLine parsed = switch (market) {
                case "1x2" -> new OddsLine(Market.WINNER, outcome, null, odds, "consenso");
                case "btts" -> new OddsLine(Market.BTTS, outcome, null, odds, "consenso");
                case "over_under_05", "over_under_15", "over_under_25", "over_under_35" ->
                    line == null ? null : new OddsLine(Market.GOALS, outcome, line, odds, "consenso");
                case "total_corners" -> line == null ? null : new OddsLine(Market.CORNERS, outcome, line, odds, "consenso");
                default -> null;
            };
            if (parsed != null && isKnownSelection(parsed)) {
                lines.add(new OddsLine(parsed.market(), parsed.selection(), parsed.line(),
                        Math.round(parsed.odds() * 100.0) / 100.0, parsed.source()));
            }
        }
        return lines;
    }

    private static boolean isKnownSelection(OddsLine line) {
        return switch (line.market()) {
            case WINNER -> List.of("HOME", "DRAW", "AWAY").contains(line.selection());
            case BTTS -> List.of("YES", "NO").contains(line.selection());
            default -> List.of("OVER", "UNDER").contains(line.selection());
        };
    }
}
