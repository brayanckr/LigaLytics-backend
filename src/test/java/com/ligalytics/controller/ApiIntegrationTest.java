package com.ligalytics.controller;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;
import com.ligalytics.repository.TeamStatsRepository;
import com.ligalytics.service.PredictionCache;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private TeamStatsRepository teamStatsRepository;

    @Autowired
    private PredictionCache predictionCache;

    private Team realMadrid;
    private Team barcelona;

    @BeforeEach
    void setUp() {
        matchRepository.deleteAll();
        teamStatsRepository.deleteAll();
        teamRepository.deleteAll();
        predictionCache.clear();

        realMadrid = teamRepository.save(Team.builder()
                .name("Real Madrid").stadium("Santiago Bernabéu")
                .marketValue(new BigDecimal("1200000000")).build());
        barcelona = teamRepository.save(Team.builder()
                .name("FC Barcelona").stadium("Camp Nou")
                .marketValue(new BigDecimal("900000000")).build());

        matchRepository.save(Match.builder()
                .matchDate(LocalDateTime.of(2025, 3, 1, 21, 0))
                .homeTeam(realMadrid).awayTeam(barcelona)
                .fullTimeHomeGoals(2).fullTimeAwayGoals(1)
                .homeXg(1.8).awayXg(1.2).corners(11).yellowCards(4).build());
        matchRepository.save(Match.builder()
                .matchDate(LocalDateTime.of(2025, 3, 8, 21, 0))
                .homeTeam(barcelona).awayTeam(realMadrid)
                .fullTimeHomeGoals(1).fullTimeAwayGoals(1)
                .homeXg(1.1).awayXg(1.3).corners(9).yellowCards(6).build());
    }

    @Test
    void listTeamsReturnsAllTeamsSorted() throws Exception {
        mockMvc.perform(get("/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("FC Barcelona")))
                .andExpect(jsonPath("$[1].name", is("Real Madrid")));
    }

    @Test
    void searchTeamsFiltersByName() throws Exception {
        mockMvc.perform(get("/teams").param("q", "madrid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Real Madrid")));
    }

    @Test
    void teamStatsReturnsAggregatesAndHistory() throws Exception {
        mockMvc.perform(get("/teams/{id}/stats", realMadrid.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamName", is("Real Madrid")))
                .andExpect(jsonPath("$.matchesPlayed", is(2)))
                .andExpect(jsonPath("$.wins", is(1)))
                .andExpect(jsonPath("$.draws", is(1)))
                .andExpect(jsonPath("$.losses", is(0)))
                .andExpect(jsonPath("$.goalsFor", is(3)))
                .andExpect(jsonPath("$.points", is(4)))
                .andExpect(jsonPath("$.recentMatches", hasSize(2)));
    }

    @Test
    void teamStatsUnknownTeamReturnsNotFound() throws Exception {
        mockMvc.perform(get("/teams/{id}/stats", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.path", is("/teams/999999/stats")));
    }

    @Test
    void rankingOrdersTeamsByPoints() throws Exception {
        mockMvc.perform(get("/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].teamName", is("Real Madrid")))
                .andExpect(jsonPath("$[0].position", is(1)))
                .andExpect(jsonPath("$[0].points", is(4)))
                .andExpect(jsonPath("$[1].teamName", is("FC Barcelona")))
                .andExpect(jsonPath("$[1].position", is(2)));
    }

    @Test
    void predictReturnsCompletePredictionWithProbabilities() throws Exception {
        mockMvc.perform(post("/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(predictBody(realMadrid.getId(), barcelona.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeTeam", is("Real Madrid")))
                .andExpect(jsonPath("$.awayTeam", is("FC Barcelona")))
                .andExpect(jsonPath("$.predictedOutcome", notNullValue()))
                .andExpect(jsonPath("$.homeWinProbability", greaterThan(0.0)))
                .andExpect(jsonPath("$.expectedHomeGoals", greaterThan(0.0)))
                .andExpect(jsonPath("$.goalsOutcome", notNullValue()))
                .andExpect(jsonPath("$.fromCache", is(false)));
    }

    @Test
    void secondPredictionIsServedFromCache() throws Exception {
        String body = predictBody(realMadrid.getId(), barcelona.getId());

        mockMvc.perform(post("/predict").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/predict").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCache", is(true)));
    }

    @Test
    void seasonsListsTheSeasonsWithData() throws Exception {
        mockMvc.perform(get("/seasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label", is("2024/2025")))
                .andExpect(jsonPath("$[0].code", is("2425")))
                .andExpect(jsonPath("$[0].matches", is(2)));
    }

    @Test
    void rankingOfAnEmptySeasonIsEmpty() throws Exception {
        mockMvc.perform(get("/ranking").param("season", "2019"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void predictionIsSavedAndExposedInTheHistory() throws Exception {
        mockMvc.perform(post("/predict").contentType(MediaType.APPLICATION_JSON)
                        .content(predictBody(realMadrid.getId(), barcelona.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mostLikelyScore", notNullValue()))
                .andExpect(jsonPath("$.strategies.resultado", notNullValue()))
                .andExpect(jsonPath("$.expectedHomeYellowCards", greaterThan(-0.001)));

        mockMvc.perform(get("/predict/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].homeTeam", is("Real Madrid")));
    }

    @Test
    void headToHeadListsMeetingsInBothVenuesWithSummaryForTheFirstTeam() throws Exception {
        mockMvc.perform(get("/teams/h2h").param("homeId", String.valueOf(realMadrid.getId()))
                        .param("awayId", String.valueOf(barcelona.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.played", is(2)))
                .andExpect(jsonPath("$.summary.winsA", is(1)))
                .andExpect(jsonPath("$.summary.draws", is(1)))
                .andExpect(jsonPath("$.summary.winsB", is(0)))
                .andExpect(jsonPath("$.summary.averageTotalGoals", is(2.5)))
                // El más reciente primero: Barcelona 1-1 Real Madrid (8 de marzo)
                .andExpect(jsonPath("$.matches[0].homeTeam", is("FC Barcelona")))
                .andExpect(jsonPath("$.matches", hasSize(2)));
    }

    @Test
    void predictionScoreAgreesWithTheWinnerAndExplainsTheGoals() throws Exception {
        mockMvc.perform(post("/predict").contentType(MediaType.APPLICATION_JSON)
                        .content(predictBody(realMadrid.getId(), barcelona.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topScores", hasSize(5)))
                .andExpect(jsonPath("$.goalFactors", not(empty())))
                .andExpect(jsonPath("$.goalsOver25Probability", greaterThan(0.0)));
    }

    @Test
    void predictUnknownTeamReturnsNotFound() throws Exception {
        mockMvc.perform(post("/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(predictBody(999999L, barcelona.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void predictInvalidBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.details", notNullValue()));
    }

    @Test
    void predictSameTeamReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(predictBody(realMadrid.getId(), realMadrid.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    private String predictBody(Long homeTeamId, Long awayTeamId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("homeTeamId", homeTeamId, "awayTeamId", awayTeamId));
    }
}
