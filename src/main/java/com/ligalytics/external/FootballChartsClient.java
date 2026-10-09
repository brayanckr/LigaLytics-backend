package com.ligalytics.external;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ligalytics.etl.TeamNameNormalizer;
import com.ligalytics.external.FootballChartsParser.ExternalFixture;

/**
 * Cliente (Proxy) de Football Charts: modelo Dixon-Coles externo con
 * probabilidades de ganador para los próximos partidos. Una sola petición
 * cubre todos los partidos y se guarda en memoria 30 minutos; si la API falla se
 * sigue sirviendo la última copia. La clave se define con
 * {@code FOOTBALL_CHARTS_API_KEY}; sin clave, el cliente queda desactivado.
 */
@Component
public class FootballChartsClient {

    public static final String SOURCE = "football-charts";

    private static final Logger log = LoggerFactory.getLogger(FootballChartsClient.class);
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(2);

    private final String apiKey;
    private final String baseUrl;
    private final String league;
    private final FootballChartsParser parser;

    private Map<String, WinnerOdds> byMatchup = Map.of();
    private Instant nextRefresh = Instant.EPOCH;

    public FootballChartsClient(
            @Value("${ligalytics.footballcharts.api-key:}") String apiKey,
            @Value("${ligalytics.footballcharts.base-url:https://footballcharts-backend.onrender.com/api/v1}") String baseUrl,
            @Value("${ligalytics.footballcharts.league:spain1}") String league,
            FootballChartsParser parser) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
        this.league = league;
        this.parser = parser;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /** Probabilidades de ganador para el enfrentamiento local-visitante, si el modelo externo las tiene. */
    public Optional<WinnerOdds> winnerOdds(String homeName, String awayName) {
        if (!isConfigured() || homeName == null || awayName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(load().get(key(homeName, awayName)));
    }

    private synchronized Map<String, WinnerOdds> load() {
        if (Instant.now().isBefore(nextRefresh)) {
            return byMatchup;
        }
        try {
            byte[] body = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build()
                    .get()
                    .uri("/leagues/{league}/fixtures/", league)
                    .retrieve()
                    .body(byte[].class);
            List<ExternalFixture> fixtures = parser.parse(new String(body, StandardCharsets.UTF_8), SOURCE);
            Map<String, WinnerOdds> map = new HashMap<>();
            for (ExternalFixture fixture : fixtures) {
                // Si un mismo cruce aparece dos veces, se conserva el primero (el más próximo).
                map.putIfAbsent(key(fixture.homeTeam(), fixture.awayTeam()), fixture.odds());
            }
            byMatchup = map;
            nextRefresh = Instant.now().plus(TTL);
        } catch (RuntimeException ex) {
            log.warn("No se pudo consultar Football Charts: {}", ex.getMessage());
            nextRefresh = Instant.now().plus(RETRY_AFTER_FAILURE);
        }
        return byMatchup;
    }

    private static String key(String home, String away) {
        return TeamNameNormalizer.canonical(home).toLowerCase() + "|" + TeamNameNormalizer.canonical(away).toLowerCase();
    }
}
