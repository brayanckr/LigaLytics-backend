package com.ligalytics.external;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Convierte la respuesta de {@code /leagues/{liga}/fixtures/} de Football Charts
 * en probabilidades de ganador por partido. Usa el bloque calibrado del modelo
 * Dixon-Coles y, si no existe, el bloque sin calibrar. Los partidos sin
 * probabilidades se omiten.
 */
@Component
public class FootballChartsParser {

    /** Partido con las probabilidades de ganador del modelo externo. */
    public record ExternalFixture(String homeTeam, String awayTeam, LocalDate date, WinnerOdds odds) {
    }

    private final ObjectMapper mapper = new ObjectMapper();

    public List<ExternalFixture> parse(String json, String source) {
        List<ExternalFixture> fixtures = new ArrayList<>();
        try {
            for (JsonNode match : mapper.readTree(json).path("matches")) {
                String home = match.path("home_team").asText(null);
                String away = match.path("away_team").asText(null);
                JsonNode model = match.path("model_predictions").path("dc_v2");
                JsonNode block = model.path("calibrated");
                if (!block.path("home").isNumber() || !block.path("away").isNumber()) {
                    block = model.path("raw");
                }
                if (home == null || away == null || !block.path("home").isNumber() || !block.path("away").isNumber()) {
                    continue;
                }
                JsonNode raw = model.path("raw");
                fixtures.add(new ExternalFixture(home, away, date(match.path("match_date").asText(null)),
                        WinnerOdds.of(block.path("home").asDouble(), block.path("away").asDouble(), source,
                                number(raw.path("expected_home_goals")), number(raw.path("expected_away_goals")))));
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Respuesta no válida de Football Charts", ex);
        }
        return fixtures;
    }

    private static Double number(JsonNode node) {
        return node.isNumber() ? node.asDouble() : null;
    }

    private static LocalDate date(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
