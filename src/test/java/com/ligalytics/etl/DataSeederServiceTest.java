package com.ligalytics.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ligalytics.model.Team;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;
import com.ligalytics.repository.TeamStatsRepository;
import com.ligalytics.service.dto.SeedReport;

@SpringBootTest
class DataSeederServiceTest {

    @Autowired
    private DataSeederService dataSeederService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private TeamStatsRepository teamStatsRepository;

    @BeforeEach
    void cleanDatabase() {
        matchRepository.deleteAll();
        teamStatsRepository.deleteAll();
        teamRepository.deleteAll();
    }

    @Test
    void seedsTwentyTeamsAndTheirMatches() {
        SeedReport report = dataSeederService.seed();

        assertEquals(20, teamRepository.count(), "deben quedar exactamente 20 equipos de LaLiga");
        assertEquals(20, report.teams());
        assertEquals(380, matchRepository.count(), "una temporada completa de 20 equipos son 380 partidos");
        assertEquals(380, report.matches());
        assertEquals(3, report.sources().size());
        assertTrue(report.sources().stream().allMatch(SeedReport.SourceResult::success), report::toString);
    }

    @Test
    void storesCanonicalTeamNames() {
        dataSeederService.seed();

        assertTrue(teamRepository.findByNameIgnoreCase("Real Madrid").isPresent());
        assertTrue(teamRepository.findByNameIgnoreCase("Atletico Madrid").isPresent());
        assertTrue(teamRepository.findByNameIgnoreCase("Barcelona").isPresent());
        assertTrue(teamRepository.findByNameIgnoreCase("Real Sociedad").isPresent());

        assertFalse(teamRepository.findByNameIgnoreCase("FC Barcelona").isPresent());
        assertFalse(teamRepository.findByNameIgnoreCase("Atletico de Madrid").isPresent());
    }

    @Test
    void enrichesTeamsWithMarketValuesAndMatchesWithXg() {
        dataSeederService.seed();

        Team madrid = teamRepository.findByNameIgnoreCase("Real Madrid").orElseThrow();
        assertNotNull(madrid.getMarketValue());
        assertTrue(madrid.getMarketValue().doubleValue() > 0);

        assertTrue(matchRepository.findAll().stream()
                .anyMatch(match -> match.getHomeXg() != null && match.getAwayXg() != null));
    }

    @Test
    void seedingIsIdempotent() {
        dataSeederService.seed();
        long teamsAfterFirst = teamRepository.count();
        long matchesAfterFirst = matchRepository.count();

        dataSeederService.seed();

        assertEquals(teamsAfterFirst, teamRepository.count());
        assertEquals(matchesAfterFirst, matchRepository.count());
    }
}
