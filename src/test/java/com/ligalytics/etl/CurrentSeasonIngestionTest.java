package com.ligalytics.etl;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.ligalytics.etl.dto.EtlSummary;
import com.ligalytics.fixtures.Fixture;
import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;
import com.ligalytics.repository.TeamStatsRepository;

@SpringBootTest
@AutoConfigureMockMvc
class CurrentSeasonIngestionTest {

    @Autowired
    private EtlService etlService;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamStatsRepository teamStatsRepository;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void clean() {
        matchRepository.deleteAll();
        teamStatsRepository.deleteAll();
        teamRepository.deleteAll();
    }

    private static Fixture fixture(String id, String home, String away, String date, String status, Integer hg, Integer ag) {
        return new Fixture(id, Instant.parse(date), status, 8, home, away, null, null, hg, ag);
    }

    @Test
    void savesOnlyFinishedMatchesAndDoesNotDuplicate() {
        List<Fixture> fixtures = List.of(
                fixture("1", "Real Racing Club de Santander", "Club Atlético de Madrid", "2026-09-20T19:00:00Z", "FINISHED", 1, 2),
                fixture("2", "Real Madrid CF", "Villarreal CF", "2026-10-10T19:00:00Z", "SCHEDULED", null, null));

        EtlSummary first = etlService.ingestFinishedFixtures(fixtures);
        EtlSummary second = etlService.ingestFinishedFixtures(fixtures);

        assertEquals(1, first.created());
        assertEquals(0, second.created());
        assertEquals(1, matchRepository.count());
        // Los equipos se normalizan entre fuentes (no se crean duplicados) y la fecha queda en hora de Madrid.
        assertEquals(List.of("Atletico Madrid", "Racing Santander"),
                teamRepository.findAll().stream().map(Team::getName).sorted().toList());
        Match match = matchRepository.findAll().get(0);
        assertEquals(LocalDateTime.of(2026, 9, 20, 21, 0), match.getMatchDate());
        assertEquals(1, match.getFullTimeHomeGoals());
    }

    @Test
    void recentMatchesMixSeasonsNewestFirst() throws Exception {
        etlService.ingestFinishedFixtures(List.of(
                fixture("1", "Real Madrid", "Barcelona", "2025-04-05T19:00:00Z", "FINISHED", 2, 1),
                fixture("2", "Barcelona", "Real Madrid", "2026-10-04T19:00:00Z", "FINISHED", 0, 0)));
        Long madridId = teamRepository.findByNameIgnoreCase("Real Madrid").orElseThrow().getId();

        mockMvc.perform(get("/teams/{id}/stats", madridId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season", is("2026/2027")))
                .andExpect(jsonPath("$.recentMatches", hasSize(2)))
                // Primero el partido de la temporada actual, despues el de la anterior.
                .andExpect(jsonPath("$.recentMatches[0].season", is("2026/2027")))
                .andExpect(jsonPath("$.recentMatches[1].season", is("2024/2025")));

        mockMvc.perform(get("/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].matchesPlayed", is(1)));
    }
}
