package com.ligalytics.fixtures;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Convierte la respuesta JSON de football-data.org (v4, /competitions/PD/matches) en {@link Fixture}. */
@Component
public class FootballDataOrgParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<Fixture> parse(String json) {
        List<Fixture> fixtures = new ArrayList<>();
        try {
            for (JsonNode match : mapper.readTree(json).path("matches")) {
                Instant date = parseDate(match.path("utcDate").asText(null));
                String home = match.path("homeTeam").path("name").asText(null);
                String away = match.path("awayTeam").path("name").asText(null);
                if (date == null || home == null || away == null) {
                    continue;
                }
                JsonNode fullTime = match.path("score").path("fullTime");
                fixtures.add(new Fixture(
                        match.path("id").asText(),
                        date,
                        normalizeStatus(match.path("status").asText()),
                        match.path("matchday").isNumber() ? match.path("matchday").asInt() : null,
                        home,
                        away,
                        match.path("homeTeam").path("crest").asText(null),
                        match.path("awayTeam").path("crest").asText(null),
                        number(fullTime.path("home")),
                        number(fullTime.path("away"))));
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Respuesta no valida del proveedor de calendario", ex);
        }
        return fixtures;
    }

    static String normalizeStatus(String status) {
        return switch (status) {
            case "SCHEDULED", "TIMED" -> "SCHEDULED";
            case "IN_PLAY", "PAUSED", "LIVE" -> "LIVE";
            case "FINISHED", "AWARDED" -> "FINISHED";
            default -> "OTHER";
        };
    }

    private static Integer number(JsonNode node) {
        return node.isNumber() ? node.asInt() : null;
    }

    private static Instant parseDate(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
