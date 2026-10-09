package com.ligalytics.betting;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligalytics.etl.TeamNameNormalizer;

/**
 * Cuotas reales de tarjetas (más/menos) de Pinnacle a través de The Odds API (mercado {@code alternate_totals_cards}).
 * El plan gratuito da 500 créditos al mes y cada consulta de un partido cuesta 1: solo se consultan partidos que
 * empiezan en menos de 48 h (antes las casas no publican este mercado), con caché de 12 h y parada de seguridad
 * cuando quedan pocos créditos. Sin clave, o si falla, el mercado de tarjetas sigue con cuotas demo.
 */
@Component
public class TheOddsApiCardsProvider {

    private record Cached<T>(T value, Instant expiresAt) {
        boolean valid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private record ApiEvent(String id, Instant kickoff, String home, String away) {
    }

    public static final String SOURCE = "pinnacle";
    private static final Logger log = LoggerFactory.getLogger(TheOddsApiCardsProvider.class);
    private static final String SPORT = "soccer_spain_la_liga";
    private static final Duration WINDOW = Duration.ofHours(48);
    private static final Duration EVENTS_TTL = Duration.ofHours(6);
    private static final Duration ODDS_TTL = Duration.ofHours(12);
    private static final Duration EMPTY_TTL = Duration.ofHours(3);
    private static final int MIN_CREDITS = 25;

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Cached<List<OddsLine>>> oddsCache = new ConcurrentHashMap<>();
    private volatile Cached<List<ApiEvent>> eventsCache;
    private volatile int creditsRemaining = Integer.MAX_VALUE;

    public TheOddsApiCardsProvider(@Value("${ligalytics.oddsapi.api-key:}") String apiKey,
            @Value("${ligalytics.oddsapi.base-url:https://api.the-odds-api.com/v4}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /** Cuotas reales de tarjetas del partido; lista vacía si no hay clave, no toca todavía o no están publicadas. */
    public List<OddsLine> cardOdds(String homeTeam, String awayTeam, Instant kickoff) {
        if (!isConfigured() || kickoff.isBefore(Instant.now()) || kickoff.isAfter(Instant.now().plus(WINDOW))) {
            return List.of();
        }
        String home = TeamNameNormalizer.canonical(homeTeam);
        String away = TeamNameNormalizer.canonical(awayTeam);
        String key = home + "|" + away + "|" + kickoff.toEpochMilli() / 3_600_000L;
        Cached<List<OddsLine>> cached = oddsCache.get(key);
        if (cached != null && cached.valid()) {
            return cached.value();
        }
        if (creditsRemaining < MIN_CREDITS) {
            return cached == null ? List.of() : cached.value();
        }
        List<OddsLine> lines = List.of();
        try {
            ApiEvent event = findEvent(home, away, kickoff);
            if (event != null) {
                lines = parse(get("/sports/" + SPORT + "/events/" + event.id() + "/odds", Map.of("regions", "eu",
                        "bookmakers", "pinnacle", "markets", "alternate_totals_cards", "oddsFormat", "decimal")));
            }
        } catch (IOException | RuntimeException ex) {
            log.warn("No se pudieron obtener las cuotas de tarjetas de {} - {}: {}", home, away, ex.getMessage());
            return cached == null ? List.of() : cached.value();
        }
        oddsCache.put(key, new Cached<>(lines, Instant.now().plus(lines.isEmpty() ? EMPTY_TTL : ODDS_TTL)));
        return lines;
    }

    private synchronized ApiEvent findEvent(String home, String away, Instant kickoff) throws IOException {
        Cached<List<ApiEvent>> cached = eventsCache;
        if (cached == null || !cached.valid()) {
            List<ApiEvent> events = new ArrayList<>();
            JsonNode root = get("/sports/" + SPORT + "/events", Map.of());
            for (JsonNode node : root) {
                events.add(new ApiEvent(node.path("id").asText(), Instant.parse(node.path("commence_time").asText()),
                        TeamNameNormalizer.canonical(node.path("home_team").asText()),
                        TeamNameNormalizer.canonical(node.path("away_team").asText())));
            }
            cached = new Cached<>(events, Instant.now().plus(EVENTS_TTL));
            eventsCache = cached;
        }
        return cached.value().stream().filter(e -> e.home().equalsIgnoreCase(home) && e.away().equalsIgnoreCase(away)
                && Math.abs(Duration.between(e.kickoff(), kickoff).toHours()) <= 6).findFirst().orElse(null);
    }

    private JsonNode get(String path, Map<String, String> params) throws IOException {
        StringBuilder url = new StringBuilder(baseUrl).append(path).append("?apiKey=")
                .append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        params.forEach((k, v) -> url.append('&').append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8)));
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
            response.headers().firstValue("x-requests-remaining").ifPresent(v -> {
                try {
                    creditsRemaining = (int) Double.parseDouble(v);
                } catch (NumberFormatException ignored) {
                    // cabecera inesperada: se mantiene el último valor conocido
                }
            });
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Consulta interrumpida", ex);
        }
    }

    /**
     * Convierte la respuesta de un evento en cuotas de tarjetas. Solo se aceptan líneas con decimal .5 (las enteras
     * pueden terminar en empate de la apuesta, que el simulador no gestiona) y cuotas válidas.
     */
    static List<OddsLine> parse(JsonNode event) {
        List<OddsLine> lines = new ArrayList<>();
        for (JsonNode bookmaker : event.path("bookmakers")) {
            for (JsonNode market : bookmaker.path("markets")) {
                if (!"alternate_totals_cards".equals(market.path("key").asText())) {
                    continue;
                }
                for (JsonNode outcome : market.path("outcomes")) {
                    String name = outcome.path("name").asText("").toUpperCase();
                    double point = outcome.path("point").asDouble(Double.NaN);
                    double price = outcome.path("price").asDouble(0.0);
                    boolean halfLine = !Double.isNaN(point) && Math.abs(point * 2 - Math.rint(point * 2)) < 1e-9
                            && Math.abs(point - Math.rint(point)) > 1e-9;
                    if (price > 1.0 && halfLine && (name.equals("OVER") || name.equals("UNDER"))) {
                        lines.add(new OddsLine(Market.CARDS, name, point, Math.round(price * 100.0) / 100.0, SOURCE));
                    }
                }
            }
        }
        lines.sort(java.util.Comparator.comparingDouble(OddsLine::line).thenComparing(OddsLine::selection));
        return lines;
    }
}
