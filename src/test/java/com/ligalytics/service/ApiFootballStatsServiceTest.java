package com.ligalytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligalytics.service.dto.MatchDetailDto.Stat;

class ApiFootballStatsServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void findsTheFixtureByTeamNamesIgnoringAliasesAndAccents() throws Exception {
        JsonNode fixtures = mapper.readTree("""
                [{"fixture":{"id":1001},"teams":{"home":{"id":10,"name":"Athletic Club"},"away":{"id":11,"name":"Atlético Madrid"}}},
                 {"fixture":{"id":1002},"teams":{"home":{"id":12,"name":"Celta Vigo"},"away":{"id":13,"name":"Levante"}}}]""");

        Optional<JsonNode> found = ApiFootballStatsService.findFixture(fixtures, "Celta Vigo", "Levante");
        assertEquals(1002, found.orElseThrow().path("fixture").path("id").asInt());

        // El orden importa (local y visitante) y los alias de LaLiga se unifican.
        assertEquals(1001, ApiFootballStatsService.findFixture(fixtures, "Athletic Bilbao", "Atletico Madrid")
                .orElseThrow().path("fixture").path("id").asInt());
        assertTrue(ApiFootballStatsService.findFixture(fixtures, "Levante", "Celta Vigo").isEmpty());
    }

    @Test
    void translatesStatisticsPerTeamAndTreatsMissingZerosAsZero() throws Exception {
        JsonNode response = mapper.readTree("""
                [{"team":{"id":12,"name":"Celta Vigo"},"statistics":[
                   {"type":"Ball Possession","value":"58%"},{"type":"Total Shots","value":14},
                   {"type":"Corner Kicks","value":7},{"type":"Yellow Cards","value":null},{"type":"Red Cards","value":null}]},
                 {"team":{"id":13,"name":"Levante"},"statistics":[
                   {"type":"Ball Possession","value":"42%"},{"type":"Total Shots","value":6},
                   {"type":"Corner Kicks","value":2},{"type":"Yellow Cards","value":3},{"type":"Red Cards","value":null}]}]""");

        List<Stat> stats = ApiFootballStatsService.parseStatistics(response, 12);

        assertEquals(List.of("possession", "shots", "corners", "yellowCards"), stats.stream().map(Stat::key).toList());
        assertEquals(58.0, stats.get(0).home());
        assertEquals(42.0, stats.get(0).away());
        // Córneres por equipo (no solo el total del partido).
        assertEquals(7.0, stats.get(2).home());
        assertEquals(2.0, stats.get(2).away());
        // Amarillas: el local no informa (0) y el visitante tiene 3; las rojas, sin dato en ninguno, se omiten.
        assertEquals(0.0, stats.get(3).home());
        assertEquals(3.0, stats.get(3).away());
    }

    @Test
    void emptyOrIncompleteResponsesGiveNoStats() throws Exception {
        assertTrue(ApiFootballStatsService.parseStatistics(mapper.readTree("[]"), 12).isEmpty());
        assertTrue(ApiFootballStatsService.parseStatistics(
                mapper.readTree("[{\"team\":{\"id\":12},\"statistics\":[]}]"), 12).isEmpty());
    }
}
