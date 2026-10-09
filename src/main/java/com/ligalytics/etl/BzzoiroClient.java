package com.ligalytics.etl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cliente de Bzzoiro Sports Data (sports.bzzoiro.com), API gratuita de fútbol
 * con xG por partido. La clave se define con {@code BZZOIRO_API_KEY}. Respeta
 * los límites de la API: pausa entre peticiones y espera de {@code Retry-After}
 * ante un 429.
 */
@Component
public class BzzoiroClient {

    private static final Logger log = LoggerFactory.getLogger(BzzoiroClient.class);
    private static final int PAGE_SIZE = 200;
    private static final int MAX_RETRIES = 3;
    private static final long PAUSE_MILLIS = 120;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String baseUrl;
    private final int leagueId;

    public BzzoiroClient(
            @Value("${ligalytics.bzzoiro.api-key:}") String apiKey,
            @Value("${ligalytics.bzzoiro.base-url:https://sports.bzzoiro.com/api/v2}") String baseUrl,
            @Value("${ligalytics.bzzoiro.league-id:3}") int leagueId) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
        this.leagueId = leagueId;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /** Partidos finalizados de LaLiga entre dos fechas (con la marca {@code has_xg}). */
    public List<JsonNode> finishedEvents(LocalDate from, LocalDate to) {
        List<JsonNode> events = new ArrayList<>();
        int offset = 0;
        while (true) {
            JsonNode page = get("/events/?league_id=" + leagueId + "&date_from=" + from + "&date_to=" + to
                    + "&status=finished&limit=" + PAGE_SIZE + "&offset=" + offset);
            page.path("results").forEach(events::add);
            if (page.path("next").isNull() || page.path("next").isMissingNode()) {
                break;
            }
            offset += PAGE_SIZE;
        }
        return events;
    }

    /** xG {local, visitante} de un partido, o vacío si la API no lo tiene. */
    public OptionalDouble[] xg(long eventId) {
        JsonNode stats = get("/events/" + eventId + "/stats/").path("stats");
        return new OptionalDouble[] { xgOf(stats.path("home")), xgOf(stats.path("away")) };
    }

    private static OptionalDouble xgOf(JsonNode team) {
        JsonNode xg = team.path("xg");
        JsonNode value = xg.isObject() ? xg.path("actual") : xg;
        if (!value.isNumber()) {
            value = team.path("expected_goals");
        }
        return value.isNumber() ? OptionalDouble.of(value.asDouble()) : OptionalDouble.empty();
    }

    private JsonNode get(String path) {
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Token " + apiKey)
                .build();
        for (int attempt = 1; ; attempt++) {
            try {
                pause(PAUSE_MILLIS);
                byte[] body = client.get().uri(path).retrieve().body(byte[].class);
                return mapper.readTree(new String(body, StandardCharsets.UTF_8));
            } catch (HttpClientErrorException.TooManyRequests ex) {
                if (attempt >= MAX_RETRIES) {
                    throw new IllegalStateException("Límite de peticiones de Bzzoiro alcanzado: " + ex.getMessage(), ex);
                }
                String retryAfter = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
                long seconds = retryAfter == null ? 2 : Math.min(60, Long.parseLong(retryAfter.trim()));
                log.warn("Bzzoiro 429: esperando {} s", seconds);
                pause(seconds * 1000);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("Respuesta no válida de Bzzoiro", ex);
            }
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Descarga interrumpida", ex);
        }
    }
}
