package com.ligalytics.service;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligalytics.AuthenticatedMockMvcConfig;
import com.ligalytics.model.Match;
import com.ligalytics.model.MatchApiStats;
import com.ligalytics.model.Team;
import com.ligalytics.repository.MatchApiStatsRepository;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;
import com.ligalytics.repository.TeamStatsRepository;
import com.ligalytics.service.dto.MatchDetailDto;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticatedMockMvcConfig.class)
class MatchDetailTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamStatsRepository teamStatsRepository;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MatchApiStatsRepository apiStatsRepository;

    @BeforeEach
    void clean() {
        apiStatsRepository.deleteAll();
        matchRepository.deleteAll();
        teamStatsRepository.deleteAll();
        teamRepository.deleteAll();
    }

    @Test
    void providerLiveMatchKeepsOnlyStatsPresentForBothTeams() throws Exception {
        JsonNode event = mapper.readTree("""
                {"status":"inprogress","current_minute":63,"home_team":"Real Madrid","away_team":"Sevilla",
                 "home_score":2,"away_score":1,"event_date":"2026-11-08T20:00:00Z"}""");
        JsonNode stats = mapper.readTree("""
                {"home":{"ball_possession":"58%","corner_kicks":6,"xg":{"actual":1.7},"yellow_cards":1},
                 "away":{"ball_possession":42,"corner_kicks":2,"xg":{"actual":0.9}}}""");

        MatchDetailDto dto = MatchDetailService.fromProvider(77, event, stats);

        assertEquals("LIVE", dto.status());
        assertEquals(63, dto.minute());
        assertEquals(2, dto.homeGoals());
        assertEquals(LocalDateTime.of(2026, 11, 8, 21, 0), dto.date());
        // yellow_cards solo viene de un equipo: no se inventa el otro lado.
        assertEquals(java.util.List.of("possession", "xg", "corners"),
                dto.stats().stream().map(MatchDetailDto.Stat::key).toList());
        assertEquals(58.0, dto.stats().get(0).home());
        assertEquals(0.9, dto.stats().get(1).away());
    }

    @Test
    void providerMatchWithoutStatsStillReturnsScore() throws Exception {
        JsonNode event = mapper.readTree("{\"status\":\"notstarted\",\"home_team\":\"A\",\"away_team\":\"B\"}");
        MatchDetailDto dto = MatchDetailService.fromProvider(5, event, null);
        assertEquals("SCHEDULED", dto.status());
        assertNull(dto.homeGoals());
        assertEquals(0, dto.stats().size());
    }

    @Test
    void playedMatchEndpointReturnsStoredStatsAndNotFound() throws Exception {
        Team home = teamRepository.save(Team.builder().name("Real Madrid").build());
        Team away = teamRepository.save(Team.builder().name("Sevilla").build());
        Match match = matchRepository.save(Match.builder().matchDate(LocalDateTime.of(2026, 10, 4, 21, 0))
                .homeTeam(home).awayTeam(away).fullTimeHomeGoals(3).fullTimeAwayGoals(1)
                .homeShots(14).awayShots(7).homeXg(2.1).awayXg(0.8).corners(9).build());

        mockMvc.perform(get("/matches/{id}", match.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FINISHED")))
                .andExpect(jsonPath("$.homeTeam", is("Real Madrid")))
                .andExpect(jsonPath("$.homeGoals", is(3)))
                .andExpect(jsonPath("$.stats", hasSize(3)))
                .andExpect(jsonPath("$.stats[?(@.key=='shots')].home", org.hamcrest.Matchers.hasItem(14.0)));

        mockMvc.perform(get("/matches/{id}", 999999)).andExpect(status().isNotFound());
    }

    @Test
    void storedApiFootballStatsReplaceTheDatabaseOnes() throws Exception {
        Team home = teamRepository.save(Team.builder().name("Celta Vigo").build());
        Team away = teamRepository.save(Team.builder().name("Levante").build());
        Match match = matchRepository.save(Match.builder().matchDate(LocalDateTime.of(2026, 11, 8, 21, 0))
                .homeTeam(home).awayTeam(away).fullTimeHomeGoals(0).fullTimeAwayGoals(0).corners(9).build());
        apiStatsRepository.save(new MatchApiStats(match.getId(), 1002L, """
                [{"key":"corners","label":"Córneres","home":7.0,"away":2.0},
                 {"key":"shots","label":"Tiros","home":14.0,"away":6.0}]""", java.time.Instant.now()));

        mockMvc.perform(get("/matches/{id}", match.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats", hasSize(2)))
                // Córneres de cada equipo, no solo el total (9) que guardaba la base de datos.
                .andExpect(jsonPath("$.stats[0].home", is(7.0)))
                .andExpect(jsonPath("$.stats[0].away", is(2.0)));
    }
}
