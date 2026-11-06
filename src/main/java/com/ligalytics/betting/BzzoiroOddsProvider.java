package com.ligalytics.betting;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    /** Un fallo del proveedor no debe esconder las cuotas 30 minutos: se reintenta enseguida. */
    private static final Duration FAILURE_TTL = Duration.ofMinutes(1);

    private final BzzoiroClient client;
    private final Map<Long, Cached<List<OddsLine>>> summaryCache = new ConcurrentHashMap<>();
    private final Map<Long, Cached<List<OddsLine>>> cornersCache = new ConcurrentHashMap<>();
    private final Set<Long> cornersInFlight = ConcurrentHashMap.newKeySet();
    /** Horario de inicio de cada partido visto, para saber cuándo hay uno en juego (ver {@link #hasMatchInLiveWindow}). */
    private final Map<Long, Instant> kickoffs = new ConcurrentHashMap<>();
    /** Hilos (daemon) para la ruta lenta de córners: pocos, para no saturar al proveedor. */
    private final ExecutorService background = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "odds-corners");
        thread.setDaemon(true);
        return thread;
    });
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
            events.forEach(event -> kickoffs.put(event.id(), event.kickoff()));
            cached = new Cached<>(events, Instant.now().plus(TTL));
            eventsCache = cached;
        }
        Instant limit = Instant.now().plus(Duration.ofDays(days));
        return cached.value().stream().filter(e -> e.kickoff().isBefore(limit)).toList();
    }

    /**
     * Si algún partido conocido está ahora en su ventana de juego (de 10 minutos antes del inicio a 3 horas y media
     * después). Solo mira la caché de partidos, nunca llama al proveedor: sirve para consultar el directo solo cuando
     * puede haber algo que ver.
     */
    public boolean hasMatchInLiveWindow() {
        Instant now = Instant.now();
        // La lista se pide con estado «notstarted»: un partido que ya empezó sale de ella al refrescarla, por eso los
        // horarios vistos se recuerdan aparte durante unas horas.
        kickoffs.values().removeIf(kickoff -> kickoff.isBefore(now.minus(Duration.ofHours(6))));
        return kickoffs.values().stream().anyMatch(kickoff ->
                kickoff.isBefore(now.plus(Duration.ofMinutes(10))) && kickoff.isAfter(now.minus(Duration.ofMinutes(210))));
    }

    /**
     * Cuotas reales del partido; lista vacía si el proveedor todavía no las publica.
     *
     * <p>Nunca espera a la ruta lenta del proveedor: ganador, goles y ambos marcan salen del resumen rápido
     * ({@code /events/{id}/odds/}, menos de un segundo) y los córners, que solo existen en {@code /odds/} (15-90 s la
     * primera vez), se piden en segundo plano y se añaden en cuanto llegan, a partir de la siguiente consulta.</p>
     */
    public List<OddsLine> odds(long eventId) {
        List<OddsLine> lines = new ArrayList<>(summary(eventId));
        Cached<List<OddsLine>> corners = cornersCache.get(eventId);
        if (corners != null) {
            lines.addAll(corners.value());
        }
        if (corners == null || !corners.valid()) {
            refreshCornersInBackground(eventId);
        }
        return lines;
    }

    /** Ganador, goles y ambos marcan (resumen rápido). Un fallo no se recuerda más de un minuto. */
    private List<OddsLine> summary(long eventId) {
        Cached<List<OddsLine>> cached = summaryCache.get(eventId);
        if (cached != null && cached.valid()) {
            return cached.value();
        }
        try {
            List<OddsLine> lines = parseSummary(client.eventOdds(eventId));
            summaryCache.put(eventId, new Cached<>(lines, Instant.now().plus(TTL)));
            return lines;
        } catch (RuntimeException ex) {
            log.warn("No se pudo obtener el resumen de cuotas del evento {}: {}", eventId, ex.getMessage());
            List<OddsLine> fallback = cached != null ? cached.value() : List.of();
            summaryCache.put(eventId, new Cached<>(fallback, Instant.now().plus(FAILURE_TTL)));
            return fallback;
        }
    }

    /** Pide los córners a la ruta lenta sin bloquear a nadie; solo una petición por partido a la vez. */
    private void refreshCornersInBackground(long eventId) {
        if (!cornersInFlight.add(eventId)) {
            return;
        }
        background.execute(() -> {
            try {
                List<OddsLine> corners = parse(client.oddsRows(eventId)).stream()
                        .filter(line -> line.market() == Market.CORNERS).toList();
                cornersCache.put(eventId, new Cached<>(corners, Instant.now().plus(TTL)));
            } catch (RuntimeException ex) {
                log.warn("No se pudieron obtener los córners del evento {}: {}", eventId, ex.getMessage());
                Cached<List<OddsLine>> previous = cornersCache.get(eventId);
                cornersCache.put(eventId, new Cached<>(previous == null ? List.of() : previous.value(),
                        Instant.now().plus(FAILURE_TTL)));
            } finally {
                cornersInFlight.remove(eventId);
            }
        });
    }

    /** Convierte el resumen rápido de cuotas en nuestros mercados: ganador, goles 1,5/2,5/3,5 y ambos marcan. */
    public static List<OddsLine> parseSummary(JsonNode odds) {
        List<OddsLine> lines = new ArrayList<>();
        addSummary(lines, odds, "home_win", Market.WINNER, "HOME", null);
        addSummary(lines, odds, "draw", Market.WINNER, "DRAW", null);
        addSummary(lines, odds, "away_win", Market.WINNER, "AWAY", null);
        for (double goals : new double[] { 1.5, 2.5, 3.5 }) {
            String key = String.valueOf((int) (goals * 10));
            addSummary(lines, odds, "over_" + key + "_goals", Market.GOALS, "OVER", goals);
            addSummary(lines, odds, "under_" + key + "_goals", Market.GOALS, "UNDER", goals);
        }
        addSummary(lines, odds, "btts_yes", Market.BTTS, "YES", null);
        addSummary(lines, odds, "btts_no", Market.BTTS, "NO", null);
        return lines;
    }

    private static void addSummary(List<OddsLine> lines, JsonNode odds, String key, Market market, String selection,
            Double line) {
        JsonNode value = odds.path(key);
        if (value.isNumber() && value.asDouble() > 1.0) {
            lines.add(new OddsLine(market, selection, line, Math.round(value.asDouble() * 100.0) / 100.0, "consenso"));
        }
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
