package com.ligalytics.fixtures;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calendario y marcadores desde football-data.org (API REST gratuita con clave,
 * LaLiga = competicion PD). La clave se define con la variable de entorno
 * {@code FOOTBALL_DATA_ORG_KEY}.
 */
@Component
public class FootballDataOrgProvider implements FixturesProvider {

    private final String apiKey;
    private final String baseUrl;
    private final String competition;
    private final FootballDataOrgParser parser;

    public FootballDataOrgProvider(
            @Value("${ligalytics.fixtures.api-key:}") String apiKey,
            @Value("${ligalytics.fixtures.base-url:https://api.football-data.org/v4}") String baseUrl,
            @Value("${ligalytics.fixtures.competition:PD}") String competition,
            FootballDataOrgParser parser) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
        this.competition = competition;
        this.parser = parser;
    }

    @Override
    public String name() {
        return "football-data.org";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public List<Fixture> fetchSeason() {
        byte[] body = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Auth-Token", apiKey)
                .build()
                .get()
                .uri("/competitions/{competition}/matches", competition)
                .retrieve()
                .body(byte[].class);
        return parser.parse(new String(body, java.nio.charset.StandardCharsets.UTF_8));
    }
}
