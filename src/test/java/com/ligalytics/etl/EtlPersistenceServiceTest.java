package com.ligalytics.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.patterns.builder.MatchAnalysis;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;

@DataJpaTest
@Import(EtlPersistenceService.class)
class EtlPersistenceServiceTest {

    @Autowired
    private EtlPersistenceService persistenceService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void resolvesTeamsAndPersistsMatch() {
        Team home = persistenceService.resolveTeam("Real Madrid");
        Team away = persistenceService.resolveTeam("FC Barcelona");

        assertNotNull(home.getId());
        assertNotNull(away.getId());
        assertEquals(2, teamRepository.count());

        MatchAnalysis analysis = MatchAnalysis.builder()
                .teams(home.getName(), away.getName())
                .matchDate(LocalDateTime.of(2025, 3, 1, 21, 0))
                .score(2, 1)
                .expectedGoals(1.8, 1.2)
                .corners(11)
                .cards(3, 1)
                .build();

        Match match = persistenceService.saveAnalysis(analysis, home, away);

        assertNotNull(match.getId());
        assertEquals(1, matchRepository.count());
        assertEquals("Real Madrid", match.getHomeTeam().getName());
        assertEquals(2, match.getFullTimeHomeGoals());
        assertEquals(1.8, match.getHomeXg());
    }

    @Test
    void resolveTeamIsIdempotent() {
        Team first = persistenceService.resolveTeam("Real Madrid");
        Team second = persistenceService.resolveTeam("real madrid");

        assertEquals(first.getId(), second.getId());
        assertEquals(1, teamRepository.count());
    }

    @Test
    void detectsExistingMatchWithinSameDay() {
        Team home = persistenceService.resolveTeam("Real Madrid");
        Team away = persistenceService.resolveTeam("FC Barcelona");

        MatchAnalysis analysis = MatchAnalysis.builder()
                .teams(home.getName(), away.getName())
                .matchDate(LocalDateTime.of(2025, 3, 1, 21, 0))
                .build();
        persistenceService.saveAnalysis(analysis, home, away);

        LocalDateTime day = LocalDateTime.of(2025, 3, 1, 0, 0);
        assertTrue(persistenceService.matchExists(
                home.getId(), away.getId(), day, day.withHour(23).withMinute(59)));
    }
}
